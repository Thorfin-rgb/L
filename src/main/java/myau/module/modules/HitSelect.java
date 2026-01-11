package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.PacketEvent;
import myau.events.UpdateEvent;
import myau.module.Module;
import myau.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.Vec3;

public class HitSelect extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    // Mode property for selecting hit prioritization strategy
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"SECOND", "CRITICALS", "W_TAP"});

    // State variables for sprinting and motion manipulation
    private boolean sprintState = false;
    private boolean set = false; // Flag to track if motion has been fixed
    private double savedSlowdown = 0.0; // Saved slowdown value from KeepSprint
    private boolean keepSprintToggled = false; // Track if we toggled KeepSprint ourselves

    // Counters for debugging/analytics (optional, can be used for logging)
    private int blockedHits = 0;
    private int allowedHits = 0;

    public HitSelect() {
        super("HitSelect", false);
    }

    /**
     * Handles update events, resetting motion in POST phase.
     */
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        if (event.getType() == EventType.POST) {
            this.resetMotion();
        }
    }

    /**
     * Intercepts packets to control attack hits based on the selected mode.
     */
    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND || event.isCancelled()) {
            return;
        }

        // Track sprint state from entity action packets
        if (event.getPacket() instanceof C0BPacketEntityAction) {
            C0BPacketEntityAction packet = (C0BPacketEntityAction) event.getPacket();
            switch (packet.getAction()) {
                case START_SPRINTING:
                    this.sprintState = true;
                    break;
                case STOP_SPRINTING:
                    this.sprintState = false;
                    break;
            }
            return;
        }

        // Handle attack packets
        if (event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity use = (C02PacketUseEntity) event.getPacket();

            if (use.getAction() != C02PacketUseEntity.Action.ATTACK) {
                return;
            }

            Entity target = use.getEntityFromWorld(mc.theWorld);
            if (target == null || target instanceof EntityLargeFireball) {
                return;
            }

            if (!(target instanceof EntityLivingBase)) {
                return;
            }

            EntityLivingBase living = (EntityLivingBase) target;
            boolean allow = true;

            // Determine if hit is allowed based on mode
            switch (this.mode.getValue()) {
                case 0: // SECOND: Prioritize second hits
                    allow = this.prioritizeSecondHit(mc.thePlayer, living);
                    break;
                case 1: // CRITICALS: Allow only critical hits
                    allow = this.prioritizeCriticalHits(mc.thePlayer);
                    break;
                case 2: // W_TAP: Prioritize W-tap hits
                    allow = this.prioritizeWTapHits(mc.thePlayer, this.sprintState);
                    break;
            }

            if (!allow) {
                event.setCancelled(true);
                this.blockedHits++;
            } else {
                this.allowedHits++;
            }
        }
    }

    /**
     * Checks if a second hit should be prioritized.
     */
    private boolean prioritizeSecondHit(EntityLivingBase player, EntityLivingBase target) {
        // Allow if target is already hurt
        if (target.hurtTime != 0) {
            return true;
        }

        // Allow if player hasn't recovered from hurt time
        if (player.hurtTime <= player.maxHurtTime - 1) {
            return true;
        }

        // Allow if too close
        double dist = player.getDistanceToEntity(target);
        if (dist < 2.5) {
            return true;
        }

        // Allow if not moving towards each other
        if (!this.isMovingTowards(target, player, 60.0)) {
            return true;
        }

        if (!this.isMovingTowards(player, target, 60.0)) {
            return true;
        }

        // Block and fix motion
        this.fixMotion();
        return false;
    }

    /**
     * Checks if a critical hit should be prioritized.
     */
    private boolean prioritizeCriticalHits(EntityLivingBase player) {
        // Allow if on ground
        if (player.onGround) {
            return true;
        }

        // Allow if hurt
        if (player.hurtTime != 0) {
            return true;
        }

        // Allow if falling (potential crit)
        if (player.fallDistance > 0.0f) {
            return true;
        }

        // Block and fix motion
        this.fixMotion();
        return false;
    }

    /**
     * Checks if a W-tap hit should be prioritized.
     */
    private boolean prioritizeWTapHits(EntityLivingBase player, boolean sprinting) {
        // Allow if against wall
        if (player.isCollidedHorizontally) {
            return true;
        }

        // Allow if not moving forward
        if (!mc.gameSettings.keyBindForward.isKeyDown()) {
            return true;
        }

        // Allow if already sprinting
        if (sprinting) {
            return true;
        }

        // Block and fix motion
        this.fixMotion();
        return false;
    }

    /**
     * Fixes motion by enabling KeepSprint and setting slowdown to 0.
     */
    private void fixMotion() {
        if (this.set) {
            return;
        }

        KeepSprint keepSprint = (KeepSprint) Myau.moduleManager.modules.get(KeepSprint.class);
        if (keepSprint == null) {
            return;
        }

        try {
            // Save current slowdown
            this.savedSlowdown = keepSprint.slowdown.getValue().doubleValue();

            // Enable KeepSprint if not already enabled, and track if we toggled it
            if (!keepSprint.isEnabled()) {
                keepSprint.toggle();
                this.keepSprintToggled = true;
            } else {
                this.keepSprintToggled = false;
            }

            // Set slowdown to 0
            keepSprint.slowdown.setValue(0);
            this.set = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Resets motion by restoring KeepSprint settings.
     */
    private void resetMotion() {
        if (!this.set) {
            return;
        }

        KeepSprint keepSprint = (KeepSprint) Myau.moduleManager.modules.get(KeepSprint.class);
        if (keepSprint == null) {
            return;
        }

        try {
            // Restore slowdown
            keepSprint.slowdown.setValue((int) this.savedSlowdown);

            // Disable KeepSprint only if we toggled it on
            if (this.keepSprintToggled && keepSprint.isEnabled()) {
                keepSprint.toggle();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.set = false;
        this.keepSprintToggled = false;
        this.savedSlowdown = 0.0;
    }

    /**
     * Checks if the source entity is moving towards the target within a max angle.
     */
    private boolean isMovingTowards(EntityLivingBase source, EntityLivingBase target, double maxAngle) {
        Vec3 currentPos = source.getPositionVector();
        Vec3 lastPos = new Vec3(source.lastTickPosX, source.lastTickPosY, source.lastTickPosZ);
        Vec3 targetPos = target.getPositionVector();

        // Calculate movement vector
        double mx = currentPos.xCoord - lastPos.xCoord;
        double mz = currentPos.zCoord - lastPos.zCoord;
        double movementLength = Math.sqrt(mx * mx + mz * mz);

        // Not moving
        if (movementLength == 0.0) {
            return false;
        }

        // Normalize movement vector
        mx /= movementLength;
        mz /= movementLength;

        // Calculate vector to target
        double tx = targetPos.xCoord - currentPos.xCoord;
        double tz = targetPos.zCoord - currentPos.zCoord;
        double targetLength = Math.sqrt(tx * tx + tz * tz);

        // Target at same position
        if (targetLength == 0.0) {
            return false;
        }

        // Normalize target vector
        tx /= targetLength;
        tz /= targetLength;

        // Dot product for angle check
        double dotProduct = mx * tx + mz * tz;
        return dotProduct >= Math.cos(Math.toRadians(maxAngle));
    }

    @Override
    public void onDisabled() {
        this.resetMotion();
        this.sprintState = false;
        this.set = false;
        this.keepSprintToggled = false;
        this.savedSlowdown = 0.0;
        this.blockedHits = 0;
        this.allowedHits = 0;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
