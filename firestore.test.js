const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
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

test("Unauthenticated user: cannot read users or chats", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("chats").get());
});

test("Authenticated user: can create and read their user profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      createdAt: new Date(),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: cannot impersonate another user profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      createdAt: new Date(),
    })
  );
});

test("Chat permissions: participant can create and read chat", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_1").set({
      id: "chat_1",
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      lastMessage: "Hello Bob!",
      lastMessageSenderId: ALICE_UID,
      lastMessageSenderName: "Alice",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Alice can get chat
  await assertSucceeds(aliceDb.collection("chats").doc("chat_1").get());

  // Bob can get chat
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("chats").doc("chat_1").get());

  // Charlie is not a participant and cannot get chat
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(charlieDb.collection("chats").doc("chat_1").get());
});

test("Messages: participant can create and read message, non-participant cannot", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Create chat first
  await aliceDb.collection("chats").doc("chat_1").set({
    id: "chat_1",
    participantIds: [ALICE_UID, BOB_UID],
    participantNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
    lastMessage: "Hello Bob!",
    lastMessageSenderId: ALICE_UID,
    lastMessageSenderName: "Alice",
    createdAt: new Date(),
    updatedAt: new Date(),
  });

  // Alice sends message
  await assertSucceeds(
    aliceDb.collection("chats").doc("chat_1").collection("messages").doc("msg_1").set({
      id: "msg_1",
      chatId: "chat_1",
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Hello Bob!",
      participantIds: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
    })
  );

  // Bob can read
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("chats").doc("chat_1").collection("messages").doc("msg_1").get());

  // Charlie cannot read
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(charlieDb.collection("chats").doc("chat_1").collection("messages").doc("msg_1").get());

  // Charlie cannot write
  await assertFails(
    charlieDb.collection("chats").doc("chat_1").collection("messages").doc("msg_2").set({
      id: "msg_2",
      chatId: "chat_1",
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      text: "Sneaky message",
      participantIds: [ALICE_UID, BOB_UID],
      createdAt: new Date(),
    })
  );
});
