package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.KeyBindUtil;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class AutoTool extends Module {
   private static final Minecraft mc = Minecraft.getMinecraft();
   private int currentToolSlot = -1;
   private int previousSlot = -1;
   private int tickDelayCounter = 0;
   public final IntProperty switchDelay = new IntProperty("delay", 0, 0, 5);
   public final BooleanProperty switchBack = new BooleanProperty("switch-back", true);
   public final BooleanProperty sneakOnly = new BooleanProperty("sneak-only", true);

   public AutoTool() {
      super("AutoTool", false);
   }

   public boolean isKillAura() {
      KillAura killAura = (KillAura)Myau.moduleManager.modules.get(KillAura.class);
      if (!killAura.isEnabled()) {
         return false;
      } else {
         return TeamUtil.isEntityLoaded(killAura.getTarget()) && killAura.isAttackAllowed();
      }
   }

   private int findShearsSlot() {
      for(int i = 0; i < 9; ++i) {
         ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
         if (stack != null && stack.getItem() instanceof ItemShears) {
            return i;
         }
      }

      return -1;
   }

   @EventTarget
   public void onTick(TickEvent event) {
      if (this.isEnabled() && event.getType() == EventType.PRE) {
         if (this.currentToolSlot != -1 && this.currentToolSlot != mc.thePlayer.inventory.currentItem) {
            this.currentToolSlot = -1;
            this.previousSlot = -1;
         }

         if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK && mc.gameSettings.keyBindAttack.isKeyDown() && !mc.thePlayer.isUsingItem() && !this.isKillAura()) {
            if (mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock() == Blocks.leaves) {
               if (this.tickDelayCounter >= (Integer)this.switchDelay.getValue() && (!(Boolean)this.sneakOnly.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()))) {
                  int slot = this.findShearsSlot();
                  if (slot != -1 && mc.thePlayer.inventory.currentItem != slot) {
                     if (this.previousSlot == -1) {
                        this.previousSlot = mc.thePlayer.inventory.currentItem;
                     }

                     mc.thePlayer.inventory.currentItem = this.currentToolSlot = slot;
                  }
               }

               ++this.tickDelayCounter;
            } else {
               ++this.tickDelayCounter;
            }
         } else {
            if ((Boolean)this.switchBack.getValue() && this.previousSlot != -1) {
               mc.thePlayer.inventory.currentItem = this.previousSlot;
            }

            this.currentToolSlot = -1;
            this.previousSlot = -1;
            this.tickDelayCounter = 0;
         }
      }

   }

   public void onDisabled() {
      this.currentToolSlot = -1;
      this.previousSlot = -1;
      this.tickDelayCounter = 0;
   }
}
