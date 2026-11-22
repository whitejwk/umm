package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.ItemStack;

public class AutoSellAllModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("command")
        .description("คำสั่งสำหรับขายของทั้งหมดในตัว")
        .defaultValue("/sellall")
        .build()
    );

    private final Setting<Boolean> requireFullStacks = sgGeneral.add(new BoolSetting.Builder()
        .name("require-full-stacks")
        .description("รอให้ทุกช่องเต็มจำนวนสูงสุด (เช่น 64 ชิ้น) ด้วยหรือไม่ หรือแค่ไม่มีช่องว่างก็ขายเลย")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> checkDelay = sgGeneral.add(new IntSetting.Builder()
        .name("check-delay")
        .description("ความถี่ในการเช็กกระเป๋า (ticks, 20 ticks = 1 วินาที)")
        .defaultValue(20)
        .min(5)
        .sliderMax(200)
        .build()
    );

    private final Setting<Integer> cooldownAfterSell = sgGeneral.add(new IntSetting.Builder()
        .name("sell-cooldown")
        .description("ระยะเวลาพักหลังพิมพ์คำสั่งขายเสร็จ ป้องกันการสแปมคำสั่ง (ticks)")
        .defaultValue(100)
        .min(20)
        .sliderMax(1200)
        .build()
    );

    private int timer = 0;

    public AutoSellAllModule() {
        super(AddonTemplate.CATEGORY, "auto-sell-all", "เช็กช่องเก็บของในตัวหากเต็มแล้วให้พิมพ์คำสั่ง /sellall อัตโนมัติ");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;

        if (timer > 0) {
            timer--;
            return;
        }

        if (isInventoryFull()) {
            ChatUtils.sendPlayerMsg(command.get());
            timer = cooldownAfterSell.get();
        } else {
            timer = checkDelay.get();
        }
    }

    private boolean isInventoryFull() {
        // เช็กเฉพาะช่องเก็บของหลัก 36 ช่อง (ช่อง Hotbar 0-8 และช่องกระเป๋า 9-35 ไม่นับช่องชุดเกราะและมือซ้าย)
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);

            // ถ้ามีช่องว่างแม้แต่ 1 ช่อง แปลว่ายังไม่เต็ม
            if (stack.isEmpty()) {
                return false;
            }

            // หากเปิด Require Full Stacks ต้องรอให้ของในช่องนั้นเต็มกอง (เช่น 64/64) ด้วย
            if (requireFullStacks.get() && stack.getCount() < stack.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }
}
