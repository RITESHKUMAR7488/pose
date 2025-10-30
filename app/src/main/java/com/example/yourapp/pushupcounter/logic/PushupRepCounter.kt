package com.example.yourapp.pushupcounter.logic

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.atan2
import kotlin.math.abs

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
        // Elbow angle when "UP"
        private const val UP_ELBOW_ANGLE_THRESHOLD = 150.0
        // Elbow angle when "DOWN"
        private const val DOWN_ELBOW_ANGLE_THRESHOLD = 90.0
        // Hip angle to ensure the body is straight
        private const val STRAIGHT_BODY_ANGLE_THRESHOLD = 150.0
        // Minimum visibility to be considered a valid pose
        private const val VISIBILITY_THRESHOLD = 0.8f
    }

    fun analyzePose(pose: Pose): Pair<Int, String> {
        // Get all visible landmarks
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)

        val landmarks = listOf(
            leftShoulder, rightShoulder, leftElbow, rightElbow,
            leftWrist, rightWrist, leftHip, rightHip, leftKnee, rightKnee
        )

        // Check if all necessary landmarks are visible
        if (landmarks.any { it == null || it.inFrameLikelihood < VISIBILITY_THRESHOLD }) {
            lastInstruction = "Keep your whole body in frame."
            return repCount to lastInstruction
        }

        // Calculate angles
        val leftElbowAngle = getAngle(leftShoulder!!, leftElbow!!, leftWrist!!)
        val rightElbowAngle = getAngle(rightShoulder!!, rightElbow!!, rightWrist!!)
        val leftHipAngle = getAngle(leftShoulder, leftHip!!, leftKnee!!)
        val rightHipAngle = getAngle(rightShoulder, rightHip!!, rightKnee!!)

        // Check for straight body
        val isBodyStraight = leftHipAngle > STRAIGHT_BODY_ANGLE_THRESHOLD &&
                rightHipAngle > STRAIGHT_BODY_ANGLE_THRESHOLD

        if (!isBodyStraight) {
            lastInstruction = "Keep your back straight!"
            return repCount to lastInstruction
        }

        // Check for "DOWN" position
        val isDownPosition = leftElbowAngle < DOWN_ELBOW_ANGLE_THRESHOLD &&
                rightElbowAngle < DOWN_ELBOW_ANGLE_THRESHOLD

        // Check for "UP" position
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
                    repCount++ // Increment rep count on UP transition
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
        firstPoint: PoseLandmark,
        midPoint: PoseLandmark,
        lastPoint: PoseLandmark
    ): Double {
        var angle = Math.toDegrees(
            (atan2(
                lastPoint.position.y - midPoint.position.y,
                lastPoint.position.x - midPoint.position.x
            ) - atan2(
                firstPoint.position.y - midPoint.position.y,
                firstPoint.position.x - midPoint.position.x
            )).toDouble()
        )
        // Ensure the angle is positive
        angle = abs(angle)
        if (angle > 180) {
            angle = 360.0 - angle
        }
        return angle
    }
}