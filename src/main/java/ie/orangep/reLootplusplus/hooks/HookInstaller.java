package ie.orangep.reLootplusplus.hooks;

import ie.orangep.reLootplusplus.command.LegacyCommandRunner;
import ie.orangep.reLootplusplus.diagnostic.LegacyWarnReporter;
import ie.orangep.reLootplusplus.runtime.RuleEngine;
import ie.orangep.reLootplusplus.runtime.RuntimeIndex;
import ie.orangep.reLootplusplus.runtime.ThrownRegistry;
import ie.orangep.reLootplusplus.runtime.BlockDropRegistry;
import ie.orangep.reLootplusplus.runtime.EntityDropRegistry;
import net.minecraft.server.MinecraftServer;

public final class HookInstaller {
    private final RuntimeIndex index;
    private final ThrownRegistry thrownRegistry;
    private final BlockDropRegistry blockDropRegistry;
    private final EntityDropRegistry entityDropRegistry;
    private final LegacyWarnReporter warnReporter;

    public HookInstaller(RuntimeIndex index, ThrownRegistry thrownRegistry, BlockDropRegistry blockDropRegistry, EntityDropRegistry entityDropRegistry, LegacyWarnReporter warnReporter) {
        this.index = index;
        this.thrownRegistry = thrownRegistry;
        this.blockDropRegistry = blockDropRegistry;
        this.entityDropRegistry = entityDropRegistry;
        this.warnReporter = warnReporter;
    }

    public void install(MinecraftServer server) {
        RuleEngine ruleEngine = new RuleEngine(index, new LegacyCommandRunner());
        ie.orangep.reLootplusplus.runtime.RuntimeState.setRuleEngine(ruleEngine);
        new ServerTickHook(ruleEngine, warnReporter).install();
        new UseItemHook(ruleEngine, warnReporter).install();
        new AttackHook(ruleEngine, warnReporter).install();
        new ThrownUseHook(ruleEngine, thrownRegistry, warnReporter).install();
        new BlockBreakHook(ruleEngine, blockDropRegistry, warnReporter).install();
        new EntityDeathHook(ruleEngine, entityDropRegistry, warnReporter).install();
    }
}
