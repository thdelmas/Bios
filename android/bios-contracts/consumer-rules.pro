# bios-contracts is pure constants + enums — nothing to obfuscate.
# Keep MetricType so reflective access (fromKey) and serialization survive.
-keep class com.bios.contracts.** { *; }
