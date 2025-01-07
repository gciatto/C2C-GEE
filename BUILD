load("@rules_jvm_external//:defs.bzl", "artifact")

java_library(
    name = "c2c_gee",
    srcs = glob(["java/it/unibo/c2c/*.java"]),
    deps = [
        ":earthengine_external",
        artifact("it.unimi.dsi:fastutil"),
        artifact("org.jspecify:jspecify"),
        artifact("com.google.guava:guava"),
    ],
)

java_library(
    name = "earthengine_external",
    srcs = [
        "java/com/google/earthengine/api/base/ArgsBase.java",
    ],
    neverlink = 1,
)

java_test(
    name = "BottomupTest",
    size = "medium",
    srcs = [
        "java/com/google/earthengine/api/base/ArgsBase.java",
        "javatests/it/unibo/c2c/BottomupTest.java",
    ],
    resources = [
        "javatests/it/unibo/c2c/testdata/input.csv",
        "javatests/it/unibo/c2c/testdata/output.csv",
    ],
    deps = [
        ":c2c_gee",
        artifact("junit:junit"),
        artifact("it.unimi.dsi:fastutil"),
    ],
)
