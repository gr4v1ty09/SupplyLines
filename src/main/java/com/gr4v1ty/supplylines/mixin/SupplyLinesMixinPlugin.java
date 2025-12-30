package com.gr4v1ty.supplylines.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class SupplyLinesMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupplyLinesMixinPlugin.class);

    public void onLoad(String mixinPackage) {
        LOGGER.debug("Mixin config loading! Package: {}", mixinPackage);
    }

    public String getRefMapperConfig() {
        return null;
    }

    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        LOGGER.debug("Checking if should apply: {} to {}", mixinClassName, targetClassName);
        return true;
    }

    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        LOGGER.debug("Accept targets called");
    }

    public List<String> getMixins() {
        LOGGER.debug("getMixins called");
        return null;
    }

    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        LOGGER.debug("PRE-APPLY: {} to {}", mixinClassName, targetClassName);
    }

    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        LOGGER.debug("POST-APPLY: {} to {}", mixinClassName, targetClassName);
    }
}
