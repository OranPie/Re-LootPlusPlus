package ie.orangep.reLootplusplus;

import ie.orangep.reLootplusplus.bootstrap.Bootstrap;
import ie.orangep.reLootplusplus.bootstrap.LuckyBootstrap;
import ie.orangep.reLootplusplus.bootstrap.LuckyCompat;
import ie.orangep.reLootplusplus.command.DumpNbtCommand;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class ReLootPlusPlus implements ModInitializer {

    @Override
    public void onInitialize() {
        // Discover and install the LuckyBlock compat implementation (if present)
        FabricLoader.getInstance()
            .getEntrypoints("re-lpp:lucky", LuckyBootstrap.class)
            .stream().findFirst().ifPresent(LuckyCompat::set);

        new Bootstrap().run();

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            DumpNbtCommand.register(dispatcher::register);
            LuckyCompat.get().registerCommands(dispatcher, dedicated);
        });
    }
}
