package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class AutoOrderModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> commandPrefix = sgGeneral.add(new StringSetting.Builder()
        .name("command")
        .description("คำสั่งหลักก่อนใส่ชื่อผู้เล่น")
        .defaultValue("/order")
        .build()
    );

    private final Setting<String> targetPlayer = sgGeneral.add(new StringSetting.Builder()
        .name("player-name")
        .description("ชื่อผู้เล่นเจ้าของออเดอร์ (เช่น ชื่อเพื่อน หรือปล่อยว่างหากไม่ใส่ชื่อ)")
        .defaultValue("")
        .build()
    );

    private final Setting<List<Item>> targetItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("target-items")
        .description("ไอเทมที่ต้องการเลือกส่งในหน้า ORDERS (เช่น Potato, Carrot)")
        .defaultValue(Items.POTATO)
        .build()
    );

    private final Setting<Integer> minSendCount = sgGeneral.add(new IntSetting.Builder()
        .name("min-send-count")
        .description("จำนวนไอเทมขั้นต่ำในกระเป๋า (ชิ้น) ถึงจะเริ่มพิมพ์คำสั่งส่งออเดอร์ (0 - 2304)")
        .defaultValue(1728)
        .min(0)
        .max(2304)
        .sliderMin(0)
        .sliderMax(2304)
        .build()
    );

    private final Setting<Boolean> autoOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open")
        .description("พิมพ์คำสั่งเปิดหน้าต่าง /order อัตโนมัติเมื่อของครบ")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> checkInterval = sgGeneral.add(new IntSetting.Builder()
        .name("check-interval")
        .description("ระยะเวลาพักหลังส่งออเดอร์เสร็จแต่ละรอบ (20 ticks = 1 วินาที)")
        .defaultValue(100)
        .min(20)
        .sliderMax(2400)
        .visible(autoOpen::get)
        .build()
    );

    private final Setting<Integer> clickDelay = sgGeneral.add(new IntSetting.Builder()
        .name("click-delay")
        .description("ความเร็วในการคลิกแต่ละหน้าต่าง (ticks)")
        .defaultValue(4)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private int intervalTimer = 0;
    private int delayTimer = 0;

    public AutoOrderModule() {
        super(AddonTemplate.CATEGORY, "auto-order", "ส่งออเดอร์อัตโนมัติ (เลือกไอเทม -> กดส่งทั้งหมด -> กดยืนยันสีเขียว)");
    }

    @Override
    public void onActivate() {
        intervalTimer = 0;
        delayTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (mc.player.containerMenu instanceof ChestMenu handler) {
            if (delayTimer > 0) {
                delayTimer--;
                return;
            }

            int rows = handler.getRowCount();

            if (rows == 6) {
                for (int i = 0; i < 45; i++) {
                    ItemStack stack = handler.getSlot(i).getItem();
                    if (!stack.isEmpty() && targetItems.get().contains(stack.getItem())) {
                        clickSlot(handler.containerId, i);
                        delayTimer = clickDelay.get();
                        return;
                    }
                }
                return;
            }

            if (rows == 3) {
                ItemStack slot15 = handler.getSlot(15).getItem();
                if (!slot15.isEmpty() && slot15.is(Items.HOPPER)) {
                    clickSlot(handler.containerId, 15);
                    delayTimer = clickDelay.get();
                    return;
                }

                ItemStack slot16 = handler.getSlot(16).getItem();
                if (!slot16.isEmpty() && (slot16.is(Items.LIME_STAINED_GLASS_PANE) || slot16.is(Items.GREEN_STAINED_GLASS_PANE))) {
                    clickSlot(handler.containerId, 16);
                    delayTimer = clickDelay.get() * 3;
                    intervalTimer = checkInterval.get();
                    return;
                }
            }
            return;
        }

        if (autoOpen.get()) {
            if (intervalTimer > 0) {
                intervalTimer--;
                return;
            }

            int currentCount = countTargetItemsInInventory();
            if (currentCount >= minSendCount.get() && currentCount > 0) {
                String name = targetPlayer.get().trim();
                String fullCmd = name.isEmpty() ? commandPrefix.get().trim() : commandPrefix.get().trim() + " " + name;

                ChatUtils.sendPlayerMsg(fullCmd);
                intervalTimer = checkInterval.get();
                delayTimer = clickDelay.get() * 2;
            } else {
                intervalTimer = 20;
            }
        }
    }

    private void clickSlot(int containerId, int slotId) {
        mc.gameMode.handleInventoryMouseClick(containerId, slotId, 0, ClickType.PICKUP, mc.player);
    }

    private int countTargetItemsInInventory() {
        int total = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && targetItems.get().contains(stack.getItem())) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
