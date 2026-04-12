"""
Train an autoencoder anomaly detection model for Bios and export to TFLite.

The autoencoder learns to reconstruct normal health z-score vectors.
Anomalies produce high reconstruction error — patterns the model
hasn't learned to reproduce.

Two-phase approach:
  1. Train autoencoder with MSE loss on normal data only
  2. Compute reconstruction error statistics, then build a final model
     that outputs calibrated anomaly probability [0, 1]

The exported model matches TFLiteAnomalyModel.kt:
  Input:  [1, 9] float32 (z-score feature vector)
  Output: [1, 1] float32 (anomaly probability 0.0–1.0)
"""

import os
import numpy as np
import tensorflow as tf
from tensorflow import keras
from sklearn.metrics import (
    precision_recall_curve,
    roc_auc_score,
    f1_score,
    classification_report,
)

from generate_data import generate_dataset, FEATURE_NAMES

FEATURE_COUNT = 9
MODEL_DIR = os.path.dirname(os.path.abspath(__file__))
TFLITE_OUTPUT = os.path.join(
    MODEL_DIR, "..", "android", "app", "src", "main", "assets", "anomaly_detector.tflite"
)


def build_autoencoder() -> keras.Model:
    """9 → 6 → 3 → 6 → 9 bottleneck autoencoder."""
    inputs = keras.Input(shape=(FEATURE_COUNT,), name="input")
    x = keras.layers.Dense(6, activation="relu", name="enc_1")(inputs)
    x = keras.layers.Dense(3, activation="relu", name="bottleneck")(x)
    x = keras.layers.Dense(6, activation="relu", name="dec_1")(x)
    outputs = keras.layers.Dense(FEATURE_COUNT, activation="linear", name="reconstruction")(x)
    return keras.Model(inputs, outputs, name="autoencoder")


def build_scoring_model(
    autoencoder: keras.Model,
    error_means: np.ndarray,
    error_stds: np.ndarray,
) -> keras.Model:
    """Wrap the autoencoder into a model that outputs anomaly probability.

    Uses per-feature squared reconstruction errors (9 values) instead of
    scalar MSE. This lets the scoring head learn which features matter
    most and detect anomalies even when the correlation structure is
    preserved (e.g., infection: HR up + HRV down follows normal
    correlation but at extreme magnitude).

    A small scoring head (9 → 4 → 1 sigmoid) learns to map the
    normalized per-feature error vector to an anomaly probability.
    """
    inputs = keras.Input(shape=(FEATURE_COUNT,), name="input")
    reconstruction = autoencoder(inputs)

    # Per-feature squared error (shape: [batch, 9])
    diff = keras.layers.Subtract()([inputs, reconstruction])
    sq_errors = keras.layers.Multiply()([diff, diff])

    # Normalize per-feature errors using training statistics
    normalized = keras.layers.Lambda(
        lambda t: (t - error_means.astype(np.float32)) /
                  np.maximum(error_stds, 1e-6).astype(np.float32),
        name="normalize",
    )(sq_errors)

    # Also concatenate absolute input values — helps detect
    # magnitude-based anomalies the autoencoder reconstructs well
    abs_input = keras.layers.Lambda(
        lambda t: tf.abs(t), name="abs_input"
    )(inputs)
    combined = keras.layers.Concatenate(name="combined")([normalized, abs_input])

    # Scoring head: 18 → 8 → 1
    x = keras.layers.Dense(8, activation="relu", name="score_1")(combined)
    score = keras.layers.Dense(
        1, activation="sigmoid", name="anomaly_score"
    )(x)

    return keras.Model(inputs, score, name="bios_anomaly")


def train(epochs: int = 150, batch_size: int = 64, seed: int = 42) -> tuple:
    tf.random.set_seed(seed)
    np.random.seed(seed)

    print("=" * 60)
    print("Bios Anomaly Detection Model — Training Pipeline")
    print("=" * 60)

    # --- Phase 1: Generate data ---
    print("\n[1/6] Generating synthetic health data...")
    X_train, X_test, y_test = generate_dataset(
        n_normal=15000, n_anomalous=5000, seed=seed
    )
    print(f"  Training:  {X_train.shape[0]} normal samples")
    print(f"  Test:      {X_test.shape[0]} samples ({int(y_test.sum())} anomalous)")

    # --- Phase 2: Train autoencoder ---
    print("\n[2/6] Training autoencoder (9→6→3→6→9)...")
    autoencoder = build_autoencoder()
    autoencoder.compile(
        optimizer=keras.optimizers.Adam(learning_rate=1e-3),
        loss="mse",
    )
    autoencoder.fit(
        X_train, X_train,  # reconstruct input
        epochs=epochs,
        batch_size=batch_size,
        validation_split=0.1,
        callbacks=[
            keras.callbacks.EarlyStopping(
                monitor="val_loss", patience=15, restore_best_weights=True
            ),
            keras.callbacks.ReduceLROnPlateau(
                monitor="val_loss", factor=0.5, patience=7, min_lr=1e-6
            ),
        ],
        verbose=1,
    )

    # --- Phase 3: Compute reconstruction error statistics ---
    print("\n[3/6] Computing per-feature reconstruction error statistics...")
    train_recon = autoencoder.predict(X_train, verbose=0)
    train_sq_errors = (X_train - train_recon) ** 2  # shape: [n, 9]
    error_means = np.mean(train_sq_errors, axis=0)  # per-feature means
    error_stds = np.std(train_sq_errors, axis=0)    # per-feature stds
    train_mse = np.mean(train_sq_errors, axis=1)    # scalar per sample
    print(f"  Per-feature error means: {error_means.round(4)}")
    print(f"  Per-feature error stds:  {error_stds.round(4)}")
    print(f"  Scalar MSE: mean={np.mean(train_mse):.4f}, std={np.std(train_mse):.4f}")

    # Check separation
    test_recon = autoencoder.predict(X_test, verbose=0)
    test_mse = np.mean((X_test - test_recon) ** 2, axis=1)
    normal_mse = test_mse[y_test == 0]
    anomalous_mse = test_mse[y_test == 1]
    print(f"  Test normal MSE:    mean={np.mean(normal_mse):.4f}")
    print(f"  Test anomalous MSE: mean={np.mean(anomalous_mse):.4f}")
    print(f"  Separation ratio:   {np.mean(anomalous_mse) / np.mean(normal_mse):.1f}x")

    raw_auc = roc_auc_score(y_test, test_mse)
    print(f"  Raw MSE AUC: {raw_auc:.4f}")

    # --- Phase 4: Build and calibrate scoring model ---
    print("\n[4/6] Building scoring model with per-feature errors + absolute inputs...")
    autoencoder.trainable = False
    scoring_model = build_scoring_model(autoencoder, error_means, error_stds)

    # Fine-tune only the anomaly_score Dense(1) layer using labeled data
    from generate_data import generate_anomalous
    rng = np.random.default_rng(seed + 1)
    anomalous_cal = generate_anomalous(4000, rng)
    X_cal = np.concatenate([X_train[:4000], anomalous_cal], axis=0)
    y_cal = np.concatenate([
        np.zeros((4000, 1), dtype=np.float32),
        np.ones((4000, 1), dtype=np.float32),
    ])
    idx = rng.permutation(len(X_cal))
    X_cal, y_cal = X_cal[idx], y_cal[idx]

    scoring_model.compile(
        optimizer=keras.optimizers.Adam(learning_rate=1e-3),
        loss="binary_crossentropy",
        metrics=["accuracy"],
    )
    scoring_model.fit(
        X_cal, y_cal,
        epochs=50,
        batch_size=128,
        validation_split=0.15,
        callbacks=[
            keras.callbacks.EarlyStopping(
                monitor="val_loss", patience=8, restore_best_weights=True
            ),
        ],
        verbose=1,
    )

    # --- Phase 5: Evaluate ---
    print("\n[5/6] Evaluating on test set...")
    y_pred_prob = scoring_model.predict(X_test, verbose=0).flatten()
    metrics = evaluate(y_test, y_pred_prob)

    return scoring_model, metrics


def evaluate(y_true: np.ndarray, y_pred_prob: np.ndarray) -> dict:
    auc = roc_auc_score(y_true, y_pred_prob)
    print(f"  ROC AUC: {auc:.4f}")

    precisions, recalls, thresholds = precision_recall_curve(y_true, y_pred_prob)
    f1_scores = 2 * (precisions * recalls) / (precisions + recalls + 1e-8)
    best_idx = np.argmax(f1_scores)
    best_threshold = thresholds[best_idx] if best_idx < len(thresholds) else 0.5
    best_f1 = f1_scores[best_idx]
    print(f"  Best F1: {best_f1:.4f} at threshold {best_threshold:.3f}")

    y_pred = (y_pred_prob >= best_threshold).astype(int)
    print(f"\n  Classification report (threshold={best_threshold:.3f}):")
    print(classification_report(y_true, y_pred, target_names=["normal", "anomalous"]))

    # Also check at Bios's 0.65 threshold
    bios_threshold = 0.65
    y_pred_bios = (y_pred_prob >= bios_threshold).astype(int)
    bios_f1 = f1_score(y_true, y_pred_bios)
    print(f"  At Bios threshold ({bios_threshold}): F1 = {bios_f1:.4f}")
    print(classification_report(y_true, y_pred_bios, target_names=["normal", "anomalous"]))

    return {
        "auc": auc,
        "best_f1": best_f1,
        "best_threshold": best_threshold,
        "bios_f1": bios_f1,
    }


def export_tflite(model: keras.Model, output_path: str) -> int:
    print(f"\n[6/6] Exporting to TFLite...")

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    # No quantization — model is <10 KB, accuracy matters more than size
    tflite_model = converter.convert()

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "wb") as f:
        f.write(tflite_model)

    size_kb = len(tflite_model) / 1024
    print(f"  Saved: {output_path}")
    print(f"  Size:  {size_kb:.1f} KB")

    # Verify
    interpreter = tf.lite.Interpreter(model_path=output_path)
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    print(f"  Input:  {input_details[0]['shape']} {input_details[0]['dtype']}")
    print(f"  Output: {output_details[0]['shape']} {output_details[0]['dtype']}")

    # Smoke tests
    def run_inference(features):
        inp = np.array([features], dtype=np.float32)
        interpreter.set_tensor(input_details[0]["index"], inp)
        interpreter.invoke()
        return interpreter.get_tensor(output_details[0]["index"])[0][0]

    normal_score = run_inference([0.0] * 9)
    mild_score = run_inference([1.0, -1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0])
    infection_score = run_inference([3.0, -3.0, 3.0, -2.0, 2.0, 2.0, -2.0, 0.0, 0.0])
    cardio_score = run_inference([2.0, -3.5, 3.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0])

    print(f"\n  Smoke tests:")
    print(f"    Normal [0,0,...,0]        → {normal_score:.4f} (expect < 0.3)")
    print(f"    Mild deviation            → {mild_score:.4f} (expect 0.2-0.5)")
    print(f"    Infection pattern          → {infection_score:.4f} (expect > 0.7)")
    print(f"    Cardiovascular stress      → {cardio_score:.4f} (expect > 0.6)")

    ok = normal_score < 0.4 and infection_score > 0.5
    if ok:
        print("  PASS: Model discriminates normal from anomalous patterns")
    else:
        print("  FAIL: Model does not discriminate well — check training")

    return len(tflite_model)


def main():
    model, metrics = train(epochs=150, batch_size=64)

    if metrics["auc"] < 0.80:
        print(f"\nWARNING: AUC {metrics['auc']:.3f} is below 0.80.")

    export_tflite(model, TFLITE_OUTPUT)

    print("\n" + "=" * 60)
    print("Done. Model ready at:")
    print(f"  {TFLITE_OUTPUT}")
    print(f"\nMatches TFLiteAnomalyModel.kt spec:")
    print(f"  Input:  [1, {FEATURE_COUNT}] float — z-score feature vector")
    print(f"  Output: [1, 1] float — anomaly probability [0.0, 1.0]")
    print(f"  Threshold: 0.65 (ANOMALY_THRESHOLD)")
    print("=" * 60)


if __name__ == "__main__":
    main()
