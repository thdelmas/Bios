"""
Regression guard for the shipped anomaly_detector.tflite (#1).

Loads the model that lives in the Android assets directory and runs the
same smoke patterns the training script exercises post-export. Exits
non-zero on regression so a CI / pre-commit hook can refuse a retrain
that silently degrades discrimination.

Anchored to the smoke checks in train.py — keep both files in lockstep
when the discrimination targets move.
"""

import os
import sys

import numpy as np
import tensorflow as tf


MODEL_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..",
    "android",
    "app",
    "src",
    "main",
    "assets",
    "anomaly_detector.tflite",
)


# (label, features, predicate). predicate takes the score and returns the
# pass condition + a human description for the failure message.
CASES = [
    (
        "normal (all zeros)",
        [0.0] * 9,
        lambda s: (s < 0.4, "expected < 0.4"),
    ),
    (
        "mild deviation",
        [1.0, -1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
        lambda s: (0.0 <= s <= 0.6, "expected in [0.0, 0.6]"),
    ),
    (
        "infection pattern",
        [3.0, -3.0, 3.0, -2.0, 2.0, 2.0, -2.0, 0.0, 0.0],
        lambda s: (s > 0.5, "expected > 0.5"),
    ),
    (
        "cardiovascular stress",
        [2.0, -3.5, 3.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0],
        lambda s: (s > 0.5, "expected > 0.5"),
    ),
]


def run_inference(interpreter, features):
    inp = np.array([features], dtype=np.float32)
    interpreter.set_tensor(interpreter.get_input_details()[0]["index"], inp)
    interpreter.invoke()
    return float(interpreter.get_tensor(interpreter.get_output_details()[0]["index"])[0][0])


def main() -> int:
    if not os.path.exists(MODEL_PATH):
        print(f"FAIL: shipped model not found at {MODEL_PATH}", file=sys.stderr)
        return 2

    interpreter = tf.lite.Interpreter(model_path=MODEL_PATH)
    interpreter.allocate_tensors()

    input_shape = interpreter.get_input_details()[0]["shape"].tolist()
    output_shape = interpreter.get_output_details()[0]["shape"].tolist()
    if input_shape != [1, 9]:
        print(f"FAIL: input shape {input_shape}, expected [1, 9]", file=sys.stderr)
        return 2
    if output_shape != [1, 1]:
        print(f"FAIL: output shape {output_shape}, expected [1, 1]", file=sys.stderr)
        return 2

    failures = []
    print("Bios anomaly model — shipped regression check")
    print("-" * 60)
    for label, features, predicate in CASES:
        score = run_inference(interpreter, features)
        ok, hint = predicate(score)
        flag = "PASS" if ok else "FAIL"
        print(f"  [{flag}] {label:32s} score={score:.4f}  ({hint})")
        if not ok:
            failures.append((label, score, hint))

    print("-" * 60)
    if failures:
        print(f"REGRESSION: {len(failures)} case(s) failed:", file=sys.stderr)
        for label, score, hint in failures:
            print(f"  - {label}: {score:.4f} ({hint})", file=sys.stderr)
        return 1

    print("OK — shipped model discriminates the smoke patterns.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
