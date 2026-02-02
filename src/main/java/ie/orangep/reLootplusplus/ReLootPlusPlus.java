package ie.orangep.reLootplusplus;

import ie.orangep.reLootplusplus.bootstrap.Bootstrap;
import net.fabricmc.api.ModInitializer;

public class ReLootPlusPlus implements ModInitializer {

    @Override
    public void onInitialize() {
        new Bootstrap().run();
    }
}
