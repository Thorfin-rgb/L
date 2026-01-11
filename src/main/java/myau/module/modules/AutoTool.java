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
   private static final Minecraft mc = Minecraft.func_71410_x();
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
         ItemStack stack = mc.field_71439_g.field_71071_by.func_70301_a(i);
         if (stack != null && stack.func_77973_b() instanceof ItemShears) {
            return i;
         }
      }

      return -1;
   }

   @EventTarget
   public void onTick(TickEvent event) {
      if (this.isEnabled() && event.getType() == EventType.PRE) {
         if (this.currentToolSlot != -1 && this.currentToolSlot != mc.field_71439_g.field_71071_by.field_70461_c) {
            this.currentToolSlot = -1;
            this.previousSlot = -1;
         }

         if (mc.field_71476_x != null && mc.field_71476_x.field_72313_a == MovingObjectType.BLOCK && mc.field_71474_y.field_74312_F.func_151470_d() && !mc.field_71439_g.func_71039_bw() && !this.isKillAura()) {
            if (mc.field_71441_e.func_180495_p(mc.field_71476_x.func_178782_a()).func_177230_c() == Blocks.field_150325_L) {
               if (this.tickDelayCounter >= (Integer)this.switchDelay.getValue() && (!(Boolean)this.sneakOnly.getValue() || KeyBindUtil.isKeyDown(mc.field_71474_y.field_74311_E.func_151463_i()))) {
                  int slot = this.findShearsSlot();
                  if (slot != -1 && mc.field_71439_g.field_71071_by.field_70461_c != slot) {
                     if (this.previousSlot == -1) {
                        this.previousSlot = mc.field_71439_g.field_71071_by.field_70461_c;
                     }

                     mc.field_71439_g.field_71071_by.field_70461_c = this.currentToolSlot = slot;
                  }
               }

               ++this.tickDelayCounter;
            } else {
               ++this.tickDelayCounter;
            }
         } else {
            if ((Boolean)this.switchBack.getValue() && this.previousSlot != -1) {
               mc.field_71439_g.field_71071_by.field_70461_c = this.previousSlot;
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
