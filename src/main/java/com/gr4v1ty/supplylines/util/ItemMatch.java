package com.gr4v1ty.supplylines.util;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;

public final class ItemMatch {
    private ItemMatch() {
    }

    public static class ItemStackKey {
        private final Item item;
        private final int hash;

        public ItemStackKey(ItemStack stack) {
            this.item = stack.getItem();
            this.hash = Objects.hashCode(this.item);
        }

        public ItemStack toStack() {
            return new ItemStack((ItemLike) this.item);
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ItemStackKey)) {
                return false;
            }
            ItemStackKey other = (ItemStackKey) obj;
            return this.item == other.item;
        }

        public int hashCode() {
            return this.hash;
        }

        public String toString() {
            ResourceLocation key = ForgeRegistries.ITEMS.getKey(this.item);
            return key != null ? key.toString() : "unknown";
        }
    }
}
