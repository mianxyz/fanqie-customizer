package io.github.mianxyz.fanqiecustomizer;
final class BarInteractionRules {
 static boolean suspend(boolean shown,boolean enabled,float alpha,boolean intersectsScreen,boolean operationOverlay){return !shown||!enabled||alpha<=0.05f||!intersectsScreen||operationOverlay;}
}
