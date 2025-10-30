package com.example.yourapp.pushupcounter.logic

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.atan2
// Defines the two states of a pushup
private enum class RepState {
    UP,
    DOWN
}

class PushupRepCounter {

    private var repCount: Int = 0
    private var currentState: RepState = RepState.UP
    private var lastInstruction = "Keep your whole body in frame."

    // Thresholds for angles (in degrees)
    companion object {
        private const val UP_ELBOW_ANGLE_THRESHOLD = 150.0
        private const val DOWN_ELBOW_ANGLE_THRESHOLD = 90.0
        private const val STRAIGHT_BODY_ANGLE_THRESHOLD = 150.0
        // MediaPipe uses 'visibility' score (0.0 to 1.0)
        private const val VISIBILITY_THRESHOLD = 0.8f
    }

    fun analyzePose(poseResult: PoseLandmarkerResult): Pair<Int, String> {
        // Check if any poses were detected
        if (poseResult.landmarks().isEmpty()) {
            lastInstruction = "Keep your whole body in frame."
            return repCount to lastInstruction
        }

        // Get the landmarks for the first (and only) detected person
        val landmarks = poseResult.landmarks()[0]

        // Get all visible landmarks using MediaPipe's constants
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftElbow = landmarks[13]
        val rightElbow = landmarks[14]
        val leftWrist = landmarks[15]
        val rightWrist = landmarks[16]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]

        val landmarkList = listOf(
            leftShoulder, rightShoulder, leftElbow, rightElbow,
            leftWrist, rightWrist, leftHip, rightHip, leftKnee, rightKnee
        )

        // Check if all landmarks are visible
        if (landmarkList.any { it.visibility() < VISIBILITY_THRESHOLD }) {
            lastInstruction = "Keep your whole body in frame."
            return repCount to lastInstruction
        }

        // Calculate angles
        val leftElbowAngle = getAngle(leftShoulder, leftElbow, leftWrist)
        val rightElbowAngle = getAngle(rightShoulder, rightElbow, rightWrist)
        val leftHipAngle = getAngle(leftShoulder, leftHip, leftKnee)
        val rightHipAngle = getAngle(rightShoulder, rightHip, rightKnee)

        // Check for straight body
        val isBodyStraight = leftHipAngle > STRAIGHT_BODY_ANGLE_THRESHOLD &&
                rightHipAngle > STRAIGHT_BODY_ANGLE_THRESHOLD

        if (!isBodyStraight) {
            lastInstruction = "Keep your back straight!"
            return repCount to lastInstruction
        }

        val isDownPosition = leftElbowAngle < DOWN_ELBOW_ANGLE_THRESHOLD &&
                rightElbowAngle < DOWN_ELBOW_ANGLE_THRESHOLD

        val isUpPosition = leftElbowAngle > UP_ELBOW_ANGLE_THRESHOLD &&
                rightElbowAngle > UP_ELBOW_ANGLE_THRESHOLD

        // State machine logic
        when (currentState) {
            RepState.UP -> {
                if (isDownPosition) {
                    currentState = RepState.DOWN
                    lastInstruction = "Go up!"
                } else {
                    lastInstruction = "Go down!"
                }
            }
            RepState.DOWN -> {
                if (isUpPosition) {
                    currentState = RepState.UP
                    repCount++
                    lastInstruction = "Go down!"
                } else {
                    lastInstruction = "Go up!"
                }
            }
        }

        return repCount to lastInstruction
    }

    // Helper function to calculate the angle between three points
    private fun getAngle(
        firstPoint: NormalizedLandmark,
        midPoint: NormalizedLandmark,
        lastPoint: NormalizedLandmark
    ): Double {
        var angle = Math.toDegrees(
            (atan2(
                lastPoint.y() - midPoint.y(),
                lastPoint.x() - midPoint.x()
            ) - atan2(
                firstPoint.y() - midPoint.y(),
                firstPoint.x() - midPoint.x()
            )).toDouble()
        )
        angle = abs(angle)
        if (angle > 180) {
            angle = 360.0 - angle
        }
        return angle
    }
}