const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write workspaceRecords", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("workspaceRecords").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workspaceRecords")
      .doc("rec_1")
      .set({
        userId: ALICE_UID,
        toolId: "MULTI_TURN_CHAT",
        toolTitle: "멀티턴 AI 채팅",
        modelEndpoint: "gemini-3.8-flash",
        prompt: "Hello",
        responseText: "Hi",
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
  );
});

test("Authenticated owner can create, read, list, update, and delete own workspaceRecord", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("workspaceRecords")
    .doc("rec_alice_1");

  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      toolId: "MULTI_TURN_CHAT",
      toolTitle: "멀티턴 AI 채팅",
      modelEndpoint: "gemini-3.8-flash",
      prompt: "Explain MVVM",
      responseText: "MVVM separates UI and state.",
      metadataSummary: "120 tokens",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("workspaceRecords").get()
  );

  await assertSucceeds(
    docRef.update({
      responseText: "Updated MVVM explanation.",
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(docRef.delete());
});

test("Cross-user access to workspaceRecords and directiveConfigs is denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("workspaceRecords")
      .doc("rec_bob_1")
      .set({
        userId: BOB_UID,
        toolId: "THINKING_REASONER",
        toolTitle: "심층 사고 추론",
        modelEndpoint: "gemini-3.8-flash",
        prompt: "Secret puzzle",
        responseText: "Secret answer",
        createdAt: new Date(),
        updatedAt: new Date(),
      });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("workspaceRecords")
      .doc("rec_bob_1")
      .get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("workspaceRecords").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("directiveConfigs")
      .doc("default")
      .set({
        userId: BOB_UID,
        commonDirective: "Hijack",
        useCommonDirective: true,
        individualDirectives: {},
        individualEnabledMap: {},
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
  );
});

test("Shadow fields and spoofed userId are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("workspaceRecords")
      .doc("rec_shadow")
      .set({
        userId: ALICE_UID,
        toolId: "MULTI_TURN_CHAT",
        toolTitle: "Chat",
        modelEndpoint: "gemini-3.8-flash",
        prompt: "Hi",
        responseText: "Hello",
        isAdmin: true,
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("directiveConfigs")
      .doc("default")
      .set({
        userId: BOB_UID,
        commonDirective: "Test",
        useCommonDirective: true,
        individualDirectives: {},
        individualEnabledMap: {},
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      })
  );
});

test("Authenticated owner can create and update independent directiveConfigs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cfgRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("directiveConfigs")
    .doc("default");

  await assertSucceeds(
    cfgRef.set({
      userId: ALICE_UID,
      commonDirective: "Always answer in Korean.",
      useCommonDirective: true,
      individualDirectives: { CODE_EXECUTION: "Verify with Python." },
      individualEnabledMap: { CODE_EXECUTION: true },
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    cfgRef.update({
      commonDirective: "Updated common directive",
      useCommonDirective: false,
      updatedAt: serverTimestamp(),
    })
  );
});
