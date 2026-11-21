const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const STUDENT_1_UID = "student_alice_123";
const STUDENT_2_UID = "student_bob_456";
const ADMIN_UID = "admin_qasim_001";
const ADMIN_EMAIL = "muhammadqasim9021@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  });
}

function spoofedEmailContext() {
  return testEnv.authenticatedContext("spoof_uid_999", {
    email: ADMIN_EMAIL,
    email_verified: false,
  });
}

function studentContext(uid) {
  return testEnv.authenticatedContext(uid, {
    email: `${uid}@gmail.com`,
    email_verified: true,
  });
}

function sampleRegistrationPayload(uid, token = "AWA-IMP-2026-00001", verifId = "verif_abc12345") {
  const ts = new Date(Date.now() - 2000);
  return {
    userId: uid,
    registrationToken: token,
    verificationId: verifId,
    studentName: "Ayesha Khan",
    fatherName: "Tariq Khan",
    className: "10th",
    section: "A",
    rollNumber: "1042",
    studentNumber: "ST-889",
    registrationNumber: "REG-2026-44",
    timingId: "slot_1",
    timingLabel: "Morning Slot (09:00 AM - 10:00 AM)",
    subjectIds: ["sub_math", "sub_phy"],
    subjectNames: ["Mathematics", "Physics"],
    paperCodes: ["AWA-MTH-26-0001", "AWA-PHY-26-0002"],
    subjectCount: 2,
    totalFeePkr: 400,
    termsAccepted: true,
    attendanceStatus: "PENDING",
    resultStatus: "PENDING",
    createdAt: ts,
    updatedAt: ts,
  };
}

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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("subjects").get());
  await assertFails(unauthDb.collection("registrations").get());
  await assertFails(unauthDb.collection("academy_settings").get());
  await assertFails(
    unauthDb.collection("registrations").doc("reg_1").set(sampleRegistrationPayload(STUDENT_1_UID))
  );
});

test("Email spoofing with email_verified=false is rejected for admin collections", async () => {
  const spoofDb = spoofedEmailContext().firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(spoofDb.collection("administrators").get());
  await assertFails(
    spoofDb.collection("subjects").doc("sub_math").set({
      name: "Mathematics",
      codePrefix: "MTH",
      assignedClasses: ["10th"],
      active: true,
      sortOrder: 1,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    spoofDb.collection("admins").doc("spoof_uid_999").set({
      uid: "spoof_uid_999",
      email: ADMIN_EMAIL,
      grantedByUid: "spoof_uid_999",
      createdAt: now,
    })
  );
});

test("Admin can configure subjects, timings, academy settings, and credentials; student cannot", async () => {
  const adminDb = adminContext().firestore();
  const aliceDb = studentContext(STUDENT_1_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    adminDb.collection("subjects").doc("sub_math").set({
      name: "Mathematics",
      codePrefix: "MTH",
      assignedClasses: ["9th", "10th"],
      active: true,
      sortOrder: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("examination_timings").doc("slot_1").set({
      slotNumber: 1,
      label: "Morning Session",
      startTime: "09:00 AM",
      endTime: "10:00 AM",
      durationMinutes: 60,
      instructions: "One hour per session.",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("academy_settings").doc("main").set({
      academyName: "AWAN ACADEMY",
      examHeading: "Improvement Crash Half Length Registration",
      instructions: "Bring hall ticket and answer sheet.",
      updatedByUid: ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Student can read catalog/config
  await assertSucceeds(aliceDb.collection("subjects").get());
  await assertSucceeds(aliceDb.collection("examination_timings").get());
  await assertSucceeds(aliceDb.collection("academy_settings").get());

  // Student cannot modify catalog/config or read administrators
  await assertFails(
    aliceDb.collection("subjects").doc("sub_math").update({ name: "Hacked", updatedAt: now })
  );
  await assertFails(aliceDb.collection("administrators").get());
});

test("Student can create own registration, paper codes, token, QR verification; cannot spoof userId, fee, or ghost fields", async () => {
  const aliceDb = studentContext(STUDENT_1_UID).firestore();
  const bobDb = studentContext(STUDENT_2_UID).firestore();
  const payload = sampleRegistrationPayload(STUDENT_1_UID);
  const ts = payload.createdAt;

  // Valid registration create
  await assertSucceeds(aliceDb.collection("registrations").doc("reg_001").set(payload));
  await assertSucceeds(
    aliceDb.collection("paper_codes").doc("AWA-MTH-26-0001").set({
      userId: STUDENT_1_UID,
      registrationId: "reg_001",
      registrationToken: "AWA-IMP-2026-00001",
      subjectId: "sub_math",
      subjectName: "Mathematics",
      paperCode: "AWA-MTH-26-0001",
      createdAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("registration_tokens").doc("AWA-IMP-2026-00001").set({
      userId: STUDENT_1_UID,
      registrationId: "reg_001",
      registrationToken: "AWA-IMP-2026-00001",
      createdAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("qr_verifications").doc("verif_abc12345").set({
      userId: STUDENT_1_UID,
      registrationId: "reg_001",
      registrationToken: "AWA-IMP-2026-00001",
      studentName: "Ayesha Khan",
      className: "10th",
      section: "A",
      timingId: "slot_1",
      timingLabel: "Morning Slot (09:00 AM - 10:00 AM)",
      subjectNames: ["Mathematics", "Physics"],
      paperCodes: ["AWA-MTH-26-0001", "AWA-PHY-26-0002"],
      attendanceStatus: "PENDING",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Scoped list query succeeds; unfiltered query fails for student
  await assertSucceeds(
    aliceDb.collection("registrations").where("userId", "==", STUDENT_1_UID).get()
  );
  await assertFails(aliceDb.collection("registrations").get());

  // Existence checks on non-existent paper_codes / tokens / qr_verifications succeed
  await assertSucceeds(bobDb.collection("paper_codes").doc("AWA-MTH-26-9999").get());
  await assertSucceeds(bobDb.collection("registration_tokens").doc("AWA-IMP-2026-99999").get());
  await assertSucceeds(bobDb.collection("qr_verifications").doc("verif_nonexistent").get());

  // Cross-user isolation: Bob cannot read or update Alice's registration or qr_verification
  await assertFails(bobDb.collection("registrations").doc("reg_001").get());
  await assertFails(bobDb.collection("qr_verifications").doc("verif_abc12345").get());
  await assertFails(
    bobDb.collection("registrations").where("userId", "==", STUDENT_1_UID).get()
  );
  await assertFails(
    aliceDb.collection("registrations").doc("reg_001").update({ attendanceStatus: "PRESENT", updatedAt: new Date(Date.now() - 500) })
  );

  // Fee mismatch / ghost field / wrong userId rejected
  await assertFails(
    aliceDb.collection("registrations").doc("reg_bad_fee").set({
      ...sampleRegistrationPayload(STUDENT_1_UID, "AWA-IMP-2026-00002", "verif_bad1"),
      totalFeePkr: 100,
    })
  );
  await assertFails(
    aliceDb.collection("registrations").doc("reg_ghost").set({
      ...sampleRegistrationPayload(STUDENT_1_UID, "AWA-IMP-2026-00003", "verif_bad2"),
      isAdmin: true,
    })
  );
  await assertFails(
    aliceDb.collection("registrations").doc("reg_spoof").set(
      sampleRegistrationPayload(STUDENT_2_UID, "AWA-IMP-2026-00004", "verif_bad3")
    )
  );
});

test("Admin can list all registrations, mark attendance, and record manual exam results; student cannot", async () => {
  const adminDb = adminContext().firestore();
  const aliceDb = studentContext(STUDENT_1_UID).firestore();
  const payload = sampleRegistrationPayload(STUDENT_1_UID);
  const ts = payload.createdAt;

  await assertSucceeds(aliceDb.collection("registrations").doc("reg_001").set(payload));

  // Admin lists all registrations
  await assertSucceeds(adminDb.collection("registrations").get());

  // Student cannot mark attendance or write results
  await assertFails(
    aliceDb.collection("attendance").doc("reg_001_slot_1").set({
      registrationId: "reg_001",
      registrationToken: "AWA-IMP-2026-00001",
      verificationId: "verif_abc12345",
      studentName: "Ayesha Khan",
      className: "10th",
      section: "A",
      timingId: "slot_1",
      timingLabel: "Morning Slot (09:00 AM - 10:00 AM)",
      status: "PRESENT",
      markedByUid: STUDENT_1_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(
    aliceDb.collection("results").doc("reg_001").set({
      registrationId: "reg_001",
      registrationToken: "AWA-IMP-2026-00001",
      userId: STUDENT_1_UID,
      studentName: "Ayesha Khan",
      fatherName: "Tariq Khan",
      className: "10th",
      section: "A",
      rollNumber: "1042",
      studentNumber: "ST-889",
      registrationNumber: "REG-2026-44",
      subjectResults: [{ subjectId: "sub_math", subjectName: "Mathematics", paperCode: "AWA-MTH-26-0001", maxMarks: 50, obtainedMarks: 45 }],
      totalMaxMarks: 50,
      totalObtainedMarks: 45,
      percentage: 90,
      remarks: "Self-edited",
      resultStatus: "PASS",
      updatedByUid: STUDENT_1_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Admin marks attendance and saves result
  await assertSucceeds(
    adminDb.collection("attendance").doc("reg_001_slot_1").set({
      registrationId: "reg_001",
      registrationToken: "AWA-IMP-2026-00001",
      verificationId: "verif_abc12345",
      studentName: "Ayesha Khan",
      className: "10th",
      section: "A",
      timingId: "slot_1",
      timingLabel: "Morning Slot (09:00 AM - 10:00 AM)",
      status: "PRESENT",
      markedByUid: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    adminDb.collection("results").doc("reg_001").set({
      registrationId: "reg_001",
      registrationToken: "AWA-IMP-2026-00001",
      userId: STUDENT_1_UID,
      studentName: "Ayesha Khan",
      fatherName: "Tariq Khan",
      className: "10th",
      section: "A",
      rollNumber: "1042",
      studentNumber: "ST-889",
      registrationNumber: "REG-2026-44",
      subjectResults: [{ subjectId: "sub_math", subjectName: "Mathematics", paperCode: "AWA-MTH-26-0001", maxMarks: 50, obtainedMarks: 45 }],
      totalMaxMarks: 50,
      totalObtainedMarks: 45,
      percentage: 90,
      remarks: "Excellent performance",
      resultStatus: "PASS",
      updatedByUid: ADMIN_UID,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Student can read own result, Bob cannot
  await assertSucceeds(aliceDb.collection("results").doc("reg_001").get());
  const bobDb = studentContext(STUDENT_2_UID).firestore();
  await assertFails(bobDb.collection("results").doc("reg_001").get());
});
