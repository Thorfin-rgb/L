package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.module.Module;
import myau.util.TimerUtil;
import myau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.potion.Potion;

public class Wtap extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();
    private boolean active = false;
    private boolean stopForward = false;
    private long delayTicks = 0L;
    private long durationTicks = 0L;

    // Delay before starting the W-tap (in ticks, converted from seconds)
    public final FloatProperty delay = new FloatProperty("delay", 5.5F, 0.0F, 10.0F);
    // Duration of the W-tap (in ticks, converted from seconds)
    public final FloatProperty duration = new FloatProperty("duration", 1.5F, 1.0F, 5.0F);

    /**
     * Checks if W-tap can be triggered based on player state.
     */
    private boolean canTrigger() {
        return !(mc.thePlayer.movementInput.moveForward < 0.8F)
                && !mc.thePlayer.isCollidedHorizontally
                && (!((float) mc.thePlayer.getFoodStats().getFoodLevel() <= 6.0F) || mc.thePlayer.capabilities.allowFlying)
                && (mc.thePlayer.isSprinting()
                || !mc.thePlayer.isUsingItem() && !mc.thePlayer.isPotionActive(Potion.blindness) && mc.gameSettings.keyBindSprint.isKeyDown());
    }

    public Wtap() {
        super("WTap", false);
    }

    /**
     * Handles movement input to control forward movement during W-tap.
     */
    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!this.active) {
            return;
        }

        // If conditions no longer allow triggering, deactivate and reset
        if (!this.stopForward && !this.canTrigger()) {
            this.deactivate();
            return;
        }

        // Handle delay phase
        if (this.delayTicks > 0L) {
            this.delayTicks -= 50L; // Decrement by one tick (50ms)
        } else {
            // Handle duration phase: Stop forward movement
            if (this.durationTicks > 0L) {
                this.durationTicks -= 50L;
                this.stopForward = true;
                mc.thePlayer.movementInput.moveForward = 0.0F;
            }

            // Deactivate if duration is over
            if (this.durationTicks <= 0L) {
                this.deactivate();
            }
        }
    }

    /**
     * Triggers W-tap on attack packets.
     */
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.isCancelled() || event.getType() != EventType.SEND) {
            return;
        }

        if (event.getPacket() instanceof C02PacketUseEntity
                && ((C02PacketUseEntity) event.getPacket()).getAction() == Action.ATTACK
                && !this.active
                && this.timer.hasTimeElapsed(500L)
                && mc.thePlayer.isSprinting()) {
            this.timer.reset();
            this.active = true;
            this.stopForward = false;
            // Set delay and duration in ticks (50ms per tick)
            this.delayTicks = (long) (50.0F * this.delay.getValue());
            this.durationTicks = (long) (50.0F * this.duration.getValue());
        }
    }

    /**
     * Deactivates W-tap and resets state.
     */
    private void deactivate() {
        this.active = false;
        this.stopForward = false;
        this.delayTicks = 0L;
        this.durationTicks = 0L;
    }

    @Override
    public void onDisabled() {
        this.deactivate();
    }
}
