package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.List;

public class AutoSellModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("command")
        .description("คำสั่งสำหรับเปิดหน้าต่างขายของ")
        .defaultValue("/sell")
        .build()
    );

    private final Setting<String> titleFilter = sgGeneral.add(new StringSetting.Builder()
        .name("window-title")
        .description("ชื่อหน้าต่าง GUI ที่อนุญาตให้โยนของและปิดอัตโนมัติ")
        .defaultValue("SELL")
        .build()
    );

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("รายการไอเทมที่ต้องการขาย")
        .defaultValue(Items.CARROT)
        .build()
    );

    private final Setting<Boolean> autoOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open")
        .description("พิมพ์คำสั่งเปิดหน้าต่างขายอัตโนมัติตามเวลา")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> sellInterval = sgGeneral.add(new IntSetting.Builder()
        .name("sell-interval")
        .description("ระยะเวลาระหว่างการพิมพ์คำสั่งขายแต่ละรอบ (20 ticks = 1 วินาที)")
        .defaultValue(600)
        .min(20)
        .sliderMax(2400)
        .visible(autoOpen::get)
        .build()
    );

    private final Setting<Integer> actionDelay = sgGeneral.add(new IntSetting.Builder()
        .name("action-delay")
        .description("ความเร็วในการย้ายของแต่ละกองและหน่วงเวลาก่อนปิดหน้าต่าง (ticks)")
        .defaultValue(2)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private int intervalTimer = 0;
    private int delayTimer = 0;

    public AutoSellModule() {
        super(AddonTemplate.CATEGORY, "auto-sell-gui", "เปิดหน้าต่างขาย โยนไอเทมขึ้นช่องขาย และปิดหน้าต่างอัตโนมัติ");
    }

    @Override
    public void onActivate() {
        intervalTimer = 0;
        delayTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // กรณีที่ 1: หน้าต่างกล่อง (GUI) เปิดอยู่
        if (mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
            if (mc.currentScreen == null) return;

            String title = mc.currentScreen.getTitle().getString();
            if (!title.toUpperCase().contains(titleFilter.get().toUpperCase())) return;

            if (delayTimer > 0) {
                delayTimer--;
                return;
            }

            int containerSlots = handler.getRows() * 9;

            // เช็กว่าช่องขายด้านบนเต็มแล้วหรือไม่
            boolean topFull = true;
            for (int i = 0; i < containerSlots; i++) {
                if (handler.getSlot(i).getStack().isEmpty()) {
                    topFull = false;
                    break;
                }
            }

            // ถ้าช่องด้านบนเต็มแล้ว ให้สั่งปิดหน้าต่างทันทีเพื่อขายของ
            if (topFull) {
                mc.player.closeHandledScreen();
                delayTimer = actionDelay.get() * 5;
                intervalTimer = sellInterval.get();
                return;
            }

            // ค้นหาไอเทมที่กำหนดไว้ในช่องเก็บของของผู้เล่น (ด้านล่าง) แล้วย้ายขึ้นไปทีละช่อง
            for (int i = containerSlots; i < handler.slots.size(); i++) {
                Slot slot = handler.getSlot(i);
                ItemStack stack = slot.getStack();

                if (!stack.isEmpty() && items.get().contains(stack.getItem())) {
                    InvUtils.shiftClick().slotId(i);
                    delayTimer = actionDelay.get();
                    return;
                }
            }

            // หากวนลูปครบแล้วไม่เหลือไอเทมเป้าหมายในตัวเลย ให้สั่งปิดหน้าต่างอัตโนมัติเพื่อรับเงิน
            mc.player.closeHandledScreen();
            delayTimer = actionDelay.get() * 5;
            intervalTimer = sellInterval.get();
            return;
        }

        // กรณีที่ 2: ไม่ได้เปิดหน้าต่างอยู่ -> นับถอยหลังเพื่อพิมพ์คำสั่ง /sell อัตโนมัติ
        if (autoOpen.get()) {
            if (intervalTimer > 0) {
                intervalTimer--;
                return;
            }

            if (hasTargetItemsInInventory()) {
                ChatUtils.sendPlayerMsg(command.get());
                intervalTimer = sellInterval.get();
                delayTimer = actionDelay.get() * 4;
            } else {
                intervalTimer = 40;
            }
        }
    }

    private boolean hasTargetItemsInInventory() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && items.get().contains(stack.getItem())) {
                return true;
            }
        }
        return false;
    }
}
