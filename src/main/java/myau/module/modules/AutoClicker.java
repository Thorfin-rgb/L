package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LeftClickMouseEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.*;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

import java.util.Objects;

public class AutoClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private boolean clickPending = false;
    private long clickDelay = 0L;
    private boolean blockHitPending = false;
    private long blockHitDelay = 0L;

    // Properties for configuration
    public final IntProperty minCPS = new IntProperty("min-cps", 8, 1, 20);
    public final IntProperty maxCPS = new IntProperty("max-cps", 12, 1, 20);
    public final BooleanProperty blockHit = new BooleanProperty("block-hit", false);
    public final FloatProperty blockHitTicks = new FloatProperty("block-hit-ticks", 1.5F, 1.0F, 20.0F, this.blockHit::getValue);
    public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty breakBlocks = new BooleanProperty("break-blocks", true);
    public final FloatProperty range = new FloatProperty("range", 3.0F, 3.0F, 8.0F, this.breakBlocks::getValue);
    public final FloatProperty hitBoxVertical = new FloatProperty("hit-box-vertical", 0.1F, 0.0F, 1.0F, this.breakBlocks::getValue);
    public final FloatProperty hitBoxHorizontal = new FloatProperty("hit-box-horizontal", 0.2F, 0.0F, 1.0F, this.breakBlocks::getValue);

    /**
     * Calculates the next click delay based on random CPS between min and max.
     */
    private long getNextClickDelay() {
        return 1000L / RandomUtil.nextLong(this.minCPS.getValue(), this.maxCPS.getValue());
    }

    /**
     * Calculates the block hit delay based on ticks.
     */
    private long getBlockHitDelay() {
        return (long) (50.0F * this.blockHitTicks.getValue());
    }

    /**
     * Checks if the player is currently breaking a block.
     */
    private boolean isBreakingBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    /**
     * Determines if clicking is allowed based on current conditions.
     */
    private boolean canClick() {
        // Check if weapons/tools are required
        if (!this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || (this.allowTools.getValue() && ItemUtil.isHoldingTool())) {
            // Special case for breaking blocks: only allow if not targeting a player and in non-survival/creative modes
            if (this.breakBlocks.getValue() && this.isBreakingBlock() && !this.hasValidTarget()) {
                GameType gameType = mc.playerController.getCurrentGameType();
                return gameType != GameType.SURVIVAL && gameType != GameType.CREATIVE;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    /**
     * Checks if the given EntityPlayer is a valid target for clicking.
     */
    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity() || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else {
                float borderSize = entityPlayer.getCollisionBorderSize();
                return RotationUtil.rayTrace(entityPlayer.getEntityBoundingBox().expand(
                        borderSize + this.hitBoxHorizontal.getValue(),
                        borderSize + this.hitBoxVertical.getValue(),
                        borderSize + this.hitBoxHorizontal.getValue()
                ), mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, this.range.getValue()) != null;
            }
        } else {
            return false;
        }
    }

    /**
     * Checks if there is any valid target (player) in range.
     */
    private boolean hasValidTarget() {
        return mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityPlayer)
                .map(e -> (EntityPlayer) e)
                .anyMatch(this::isValidTarget);
    }

    public AutoClicker() {
        super("AutoClicker", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            // Decrement delays
            if (this.clickDelay > 0L) {
                this.clickDelay -= 50L;
            }
            if (this.blockHitDelay > 0L) {
                this.blockHitDelay -= 50L;
            }

            // Reset pendings if GUI is open
            if (mc.currentScreen != null) {
                this.clickPending = false;
                this.blockHitPending = false;
            } else {
                // Handle pending clicks
                if (this.clickPending) {
                    this.clickPending = false;
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
                }
                if (this.blockHitPending) {
                    this.blockHitPending = false;
                    KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
                }

                // Main autoclick logic
                if (this.isEnabled() && this.canClick() && mc.gameSettings.keyBindAttack.isKeyDown()) {
                    if (!mc.thePlayer.isUsingItem()) {
                        // Perform clicks while delay is <= 0, but limit to prevent excessive clicking in one tick
                        int clicksThisTick = 0;
                        while (this.clickDelay <= 0L && clicksThisTick < 10) { // Cap to 10 clicks per tick for safety
                            this.clickPending = true;
                            this.clickDelay += this.getNextClickDelay();
                            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                            KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());
                            clicksThisTick++;
                        }
                    }

                    // Block hit logic
                    if (this.blockHit.getValue()
                            && this.blockHitDelay <= 0L
                            && mc.gameSettings.keyBindUseItem.isKeyDown()
                            && ItemUtil.isHoldingSword()) {
                        this.blockHitPending = true;
                        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                        if (!mc.thePlayer.isUsingItem()) {
                            this.blockHitDelay += this.getBlockHitDelay();
                            KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindUseItem.getKeyCode());
                        }
                    }
                }
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onClick(LeftClickMouseEvent event) {
        if (this.isEnabled() && !event.isCancelled()) {
            if (!this.clickPending) {
                this.clickDelay += this.getNextClickDelay();
            }
        }
    }

    @Override
    public void onEnabled() {
        this.clickDelay = 0L;
        this.blockHitDelay = 0L;
    }

    @Override
    public void verifyValue(String mode) {
        if (this.minCPS.getName().equals(mode)) {
            if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                this.maxCPS.setValue(this.minCPS.getValue());
            }
        } else if (this.maxCPS.getName().equals(mode)) {
            if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                this.minCPS.setValue(this.maxCPS.getValue());
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return Objects.equals(this.minCPS.getValue(), this.maxCPS.getValue())
                ? new String[]{this.minCPS.getValue().toString()}
                : new String[]{String.format("%d-%d", this.minCPS.getValue(), this.maxCPS.getValue())};
    }
}
