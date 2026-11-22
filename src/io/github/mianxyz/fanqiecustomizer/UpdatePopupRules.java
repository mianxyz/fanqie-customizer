package io.github.mianxyz.fanqiecustomizer;

/** Automatic app upgrades only; security, login and unrelated updates stay intact. */
final class UpdatePopupRules {
    static final String POP="com.dragon.read.pop.PopDefiner$Pop";
    private UpdatePopupRules() {}

    static boolean block(boolean enabled,String enumClass,String name) {
        if(!enabled||!POP.equals(enumClass)||name==null)return false;
        switch(name) {
            case "gray_upgrade_dialog":case "official_upgrade_dialog":case "force_upgrade_dialog":return true;
            default:return false;
        }
    }
}
