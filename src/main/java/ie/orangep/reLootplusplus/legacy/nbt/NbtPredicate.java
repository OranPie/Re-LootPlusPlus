package ie.orangep.reLootplusplus.legacy.nbt;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.Iterator;

public final class NbtPredicate {
    private NbtPredicate() {
    }

    public static boolean matches(NbtCompound haystack, NbtCompound needle) {
        if (needle == null) {
            return true;
        }
        if (haystack == null) {
            return false;
        }
        for (String key : needle.getKeys()) {
            NbtElement expected = needle.get(key);
            NbtElement actual = haystack.get(key);
            if (!matchesElement(actual, expected)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesElement(NbtElement actual, NbtElement expected) {
        if (expected == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        if (expected instanceof NbtCompound expectedCompound) {
            if (!(actual instanceof NbtCompound actualCompound)) {
                return false;
            }
            return matches(actualCompound, expectedCompound);
        }
        if (expected instanceof NbtList expectedList) {
            if (!(actual instanceof NbtList actualList)) {
                return false;
            }
            return listContainsAll(actualList, expectedList);
        }
        return expected.equals(actual);
    }

    private static boolean listContainsAll(NbtList actual, NbtList expected) {
        if (expected.isEmpty()) {
            return true;
        }
        for (NbtElement exp : expected) {
            boolean found = false;
            for (NbtElement act : actual) {
                if (matchesElement(act, exp)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
