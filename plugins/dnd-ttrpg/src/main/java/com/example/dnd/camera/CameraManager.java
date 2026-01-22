package com.example.dnd.camera;

import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.MouseInputType;
import com.hypixel.hytale.protocol.MovementForceRotationType;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Manages camera settings for the D&D top-down view.
 */
public class CameraManager {
    // Camera configuration for CRPG-style top-down view
    private static final float TOPDOWN_DISTANCE = 25.0F;
    private static final float TOPDOWN_PITCH = (float) (-Math.PI / 2); // Straight down
    private static final float LERP_SPEED = 0.15F;

    /**
     * Apply the top-down CRPG camera to a player.
     */
    public static void applyTopDownCamera(PlayerRef playerRef) {
        ServerCameraSettings settings = createTopDownSettings();
        playerRef.getPacketHandler().writeNoCache(
            new SetServerCamera(ClientCameraView.Custom, true, settings)
        );
    }

    /**
     * Reset the camera to default third-person view.
     */
    public static void resetCamera(PlayerRef playerRef) {
        playerRef.getPacketHandler().writeNoCache(
            new SetServerCamera(ClientCameraView.ThirdPerson, false, null)
        );
    }

    /**
     * Create the camera settings for top-down view.
     */
    private static ServerCameraSettings createTopDownSettings() {
        ServerCameraSettings settings = new ServerCameraSettings();

        // Smooth camera movement
        settings.positionLerpSpeed = LERP_SPEED;
        settings.rotationLerpSpeed = LERP_SPEED;

        // Distance from player (height for top-down)
        settings.distance = TOPDOWN_DISTANCE;

        // Third-person view settings
        settings.isFirstPerson = false;
        settings.displayCursor = true;  // Show mouse cursor for clicking
        settings.displayReticle = false; // Hide crosshair

        // Eye offset for better positioning
        settings.eyeOffset = true;
        settings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;

        // Custom rotation (straight down)
        settings.rotationType = RotationType.Custom;
        settings.rotation = new Direction(0.0F, TOPDOWN_PITCH, 0.0F);
        settings.movementForceRotationType = MovementForceRotationType.Custom;

        // Mouse input for clicking on the ground plane
        settings.mouseInputType = MouseInputType.LookAtPlane;
        settings.planeNormal = new Vector3f(0.0F, 1.0F, 0.0F); // XZ horizontal plane

        return settings;
    }

    /**
     * Create settings for an isometric (angled top-down) camera.
     * This provides a more traditional RPG view.
     */
    public static void applyIsometricCamera(PlayerRef playerRef) {
        ServerCameraSettings settings = new ServerCameraSettings();

        settings.positionLerpSpeed = LERP_SPEED;
        settings.rotationLerpSpeed = LERP_SPEED;
        settings.distance = 20.0F;
        settings.isFirstPerson = false;
        settings.displayCursor = true;
        settings.displayReticle = false;
        settings.eyeOffset = true;
        settings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffset;

        // 45-degree angle for isometric view
        float isometricPitch = (float) (-Math.PI / 4); // 45 degrees down
        settings.rotationType = RotationType.Custom;
        settings.rotation = new Direction(0.0F, isometricPitch, 0.0F);
        settings.movementForceRotationType = MovementForceRotationType.Custom;

        settings.mouseInputType = MouseInputType.LookAtPlane;
        settings.planeNormal = new Vector3f(0.0F, 1.0F, 0.0F);

        playerRef.getPacketHandler().writeNoCache(
            new SetServerCamera(ClientCameraView.Custom, true, settings)
        );
    }
}
