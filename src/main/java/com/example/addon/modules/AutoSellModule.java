package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AutoSellModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgReplant = settings.createGroup("Auto Replant");

    // --- ตั้งค่า Auto Sell ---
    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("command")
        .description("คำสั่งสำหรับเปิดหน้าต่างขายของ")
        .defaultValue("/sell")
        .build()
    );

    private final Setting<Integer> rowsFilter = sgGeneral.add(new IntSetting.Builder()
        .name("chest-rows")
        .description("จำนวนแถวของหน้าต่าง SELL (ปกติคือ 4 แถว)")
        .defaultValue(4)
        .min(1)
        .max(6)
        .sliderMin(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("รายการไอเทมที่ต้องการขาย")
        .defaultValue(Items.CARROT)
        .build()
    );

    private final Setting<Integer> minSellCount = sgGeneral.add(new IntSetting.Builder()
        .name("min-sell-count")
        .description("จำนวนไอเทมขั้นต่ำในกระเป๋าทั้งหมด (ชิ้น) ที่จะเริ่มพิมพ์คำสั่งขาย (0 - 2304)")
        .defaultValue(1728)
        .min(0)
        .max(2304)
        .sliderMin(0)
        .sliderMax(2304)
        .build()
    );

    private final Setting<Integer> keepStacks = sgGeneral.add(new IntSetting.Builder()
        .name("keep-stacks")
        .description("จำนวนกองที่ต้องการเก็บไว้ในตัวไม่เอาไปขาย (เช่น 9 กอง)")
        .defaultValue(9)
        .min(0)
        .max(36)
        .sliderMin(0)
        .sliderMax(36)
        .build()
    );

    private final Setting<Boolean> autoOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open")
        .description("พิมพ์คำสั่งเปิดหน้าต่างขายอัตโนมัติเมื่อจำนวนไอเทมครบ")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> sellInterval = sgGeneral.add(new IntSetting.Builder()
        .name("sell-interval")
        .description("ระยะเวลาพักขั้นต่ำหลังขายเสร็จแต่ละรอบ (20 ticks = 1 วินาที)")
        .defaultValue(100)
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

    // --- ตั้งค่า Auto Replant ---
    private final Setting<Boolean> autoReplant = sgReplant.add(new BoolSetting.Builder()
        .name("auto-replant")
        .description("ปลูกแครอทคืนลงบนดินพรวนที่ว่างอยู่รอบตัวอัตโนมัติ")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> replantRange = sgReplant.add(new IntSetting.Builder()
        .name("replant-range")
        .description("รัศมีในการค้นหาดินว่างเพื่อปลูกคืน (บล็อก)")
        .defaultValue(4)
        .min(1)
        .max(5)
        .sliderMin(1)
        .sliderMax(5)
        .visible(autoReplant::get)
        .build()
    );

    private final Setting<Integer> replantDelay = sgReplant.add(new IntSetting.Builder()
        .name("replant-delay")
        .description("ความเร็วในการวางแครอทแต่ละต้น (ticks)")
        .defaultValue(2)
        .min(1)
        .sliderMax(10)
        .visible(autoReplant::get)
        .build()
    );

    private int intervalTimer = 0;
    private int delayTimer = 0;
    private int replantTimer = 0;

    public AutoSellModule() {
        super(AddonTemplate.CATEGORY, "auto-sell-gui", "เปิดหน้าต่างขายเมื่อของครบจำนวน เหลือไว้ตามกองที่ตั้ง และปิดหน้าต่างอัตโนมัติ");
    }

    @Override
    public void onActivate() {
        intervalTimer = 0;
        delayTimer = 0;
        replantTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        // ==========================================
        // ส่วนที่ 1: จัดการหน้าต่าง SELL (โยนของ + ปิดหน้าต่าง)
        // ==========================================
        if (mc.player.containerMenu instanceof ChestMenu handler) {
            if (handler.getRowCount() != rowsFilter.get()) return;

            if (delayTimer > 0) {
                delayTimer--;
                return;
            }

            int containerSlots = handler.getRowCount() * 9;

            boolean topFull = true;
            for (int i = 0; i < containerSlots; i++) {
                if (handler.getSlot(i).getItem().isEmpty()) {
                    topFull = false;
                    break;
                }
            }

            if (topFull) {
                mc.player.closeContainer();
                delayTimer = actionDelay.get() * 5;
                intervalTimer = sellInterval.get();
                return;
            }

            // นับจำนวนกองเป้าหมายทั้งหมดที่มีอยู่ในช่องกระเป๋าของผู้เล่น
            int totalStacks = 0;
            for (int i = containerSlots; i < handler.slots.size(); i++) {
                ItemStack stack = handler.getSlot(i).getItem();
                if (!stack.isEmpty() && items.get().contains(stack.getItem())) {
                    totalStacks++;
                }
            }

            // หากจำนวนกองในตัวยังมากกว่าจำนวนที่ตั้งค่าให้เก็บไว้ (เช่น มากกว่า 9 กอง) ให้โยนขึ้นไปขาย
            if (totalStacks > keepStacks.get()) {
                for (int i = containerSlots; i < handler.slots.size(); i++) {
                    Slot slot = handler.getSlot(i);
                    ItemStack stack = slot.getItem();

                    if (!stack.isEmpty() && items.get().contains(stack.getItem())) {
                        InvUtils.shiftClick().slotId(i);
                        delayTimer = actionDelay.get();
                        return;
                    }
                }
            }

            // เมื่อเหลือครบ 9 กองตามที่ตั้งไว้แล้ว -> ปิดหน้าต่างรับเงินทันที
            mc.player.closeContainer();
            delayTimer = actionDelay.get() * 5;
            intervalTimer = sellInterval.get();
            return;
        }

        // ==========================================
        // ส่วนที่ 2: ระบบปลูกแครอทคืนอัตโนมัติ (Auto Replant)
        // ==========================================
        if (autoReplant.get()) {
            if (replantTimer > 0) {
                replantTimer--;
            } else {
                if (tryReplantCarrot()) {
                    replantTimer = replantDelay.get();
                }
            }
        }

        // ==========================================
        // ส่วนที่ 3: เช็กจำนวนไอเทมในกระเป๋าเพื่อพิมพ์คำสั่ง /sell อัตโนมัติ
        // ==========================================
        if (autoOpen.get()) {
            if (intervalTimer > 0) {
                intervalTimer--;
                return;
            }

            int currentItemCount = countTargetItemsInInventory();
            int stackCount = countTargetStacksInInventory();

            // ต้องมีจำนวนชิ้นถึงเกณฑ์ที่ตั้งไว้ (min-sell-count) และมีจำนวนกองมากกว่าที่จะเก็บไว้ (มากกว่า 9 กอง)
            boolean hasEnoughCount = currentItemCount >= minSellCount.get();
            boolean hasExcessStacks = stackCount > keepStacks.get();

            if (hasEnoughCount && hasExcessStacks) {
                ChatUtils.sendPlayerMsg(command.get());
                intervalTimer = sellInterval.get();
                delayTimer = actionDelay.get() * 4;
            } else {
                intervalTimer = 20; // เช็กใหม่ทุกๆ 1 วินาที
            }
        }
    }

    private boolean tryReplantCarrot() {
        FindItemResult carrot = InvUtils.findInHotbar(Items.CARROT);
        if (!carrot.found()) {
            FindItemResult invCarrot = InvUtils.find(Items.CARROT);
            if (invCarrot.found()) {
                InvUtils.move().from(invCarrot.slot()).toHotbar(8);
            }
            return false;
        }

        BlockPos playerPos = mc.player.blockPosition();
        int r = replantRange.get();

        for (int x = -r; x <= r; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos farmlandPos = playerPos.offset(x, y, z);
                    BlockPos cropPos = farmlandPos.above();

                    if (mc.level.getBlockState(farmlandPos).is(Blocks.FARMLAND) &&
                        mc.level.getBlockState(cropPos).isAir()) {

                        if (mc.player.distanceToSqr(Vec3.atCenterOf(farmlandPos)) > 20.25) continue;

                        InvUtils.swap(carrot.slot(), true);

                        Vec3 hitVec = new Vec3(
                            farmlandPos.getX() + 0.5,
                            farmlandPos.getY() + 1.0,
                            farmlandPos.getZ() + 0.5
                        );
                        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, farmlandPos, false);

                        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);

                        InvUtils.swapBack();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int countTargetItemsInInventory() {
        int total = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && items.get().contains(stack.getItem())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private int countTargetStacksInInventory() {
        int stacks = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && items.get().contains(stack.getItem())) {
                stacks++;
            }
        }
        return stacks;
    }
}
