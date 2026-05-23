package com.bios.app.engine

/**
 * Why a PPG recording was not accepted by [PpgSignalProcessor]. The user-
 * facing string is surfaced verbatim by the capture screen — keep it short
 * and actionable.
 */
enum class RejectionReason(val userMessage: String) {
    INSUFFICIENT_RECORDING_TIME("Recording was too short — try again and hold for the full countdown."),
    INSUFFICIENT_SIGNAL("Finger not detected — place fingertip fully over the rear camera and flash."),
    SATURATION("Too much light — lighten finger pressure or move away from bright light."),
    MOTION_ARTIFACT("Motion detected — rest your hand on a surface and hold still."),
    TOO_FEW_BEATS("Signal too weak to extract a heartbeat — check contact and retry."),
    IRREGULAR_RHYTHM("Signal too irregular to score — retry with a steadier hand."),
    HARDWARE_UNAVAILABLE("Camera or flash unavailable — this device may not support fingertip-PPG capture."),
}
