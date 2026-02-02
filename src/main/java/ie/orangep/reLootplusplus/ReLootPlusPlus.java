package ie.orangep.reLootplusplus;

import ie.orangep.reLootplusplus.bootstrap.Bootstrap;
import ie.orangep.reLootplusplus.command.DumpNbtCommand;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.api.ModInitializer;

public class ReLootPlusPlus implements ModInitializer {

    @Override
    public void onInitialize() {
        new Bootstrap().run();
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            DumpNbtCommand.register(dispatcher::register);
        });
    }
}
