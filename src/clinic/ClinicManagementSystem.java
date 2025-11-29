package clinic;

import clinic.Models.*;
import java.util.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public class ClinicManagementSystem {

    // ------------------- FILE LOCATIONS -------------------
    private final String PATIENTS_FILE = "patients.csv";
    private final String DOCTORS_FILE = "doctors.csv";
    private final String HISTORY_FILE = "patient_history.csv";

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ------------------- DATA STRUCTURES -------------------
    private final PriorityQueue<Patient> waitingQueue = new PriorityQueue<>();
    private final HashMap<String, List<ClinicHistoryRecord>> patientHistoryMap = new HashMap<>();
    private final HashMap<String, String> patientNames = new HashMap<>();
    private final HashMap<String, Doctor> doctorMap = new HashMap<>();
    private final HashMap<String, Room> roomMap = new HashMap<>();

    // ------------------- CONSTRUCTOR -------------------
    public ClinicManagementSystem() {
        loadPatients();
        loadDoctors();
        loadHistory();
    }

    // ------------------- ID GENERATOR -------------------
    private String generateShortID(String prefix) {
        String shortId;
        int attempts = 0;

        do {
            long randomBytes = ThreadLocalRandom.current().nextLong(0, 0x100000000L);
            String hexPart = String.format("%08x", randomBytes);
            shortId = prefix + hexPart;

            attempts++;

            if (prefix.equals("P") && !patientNames.containsKey(shortId)) return shortId;
            if (prefix.equals("D") && !doctorMap.containsKey(shortId)) return shortId;
            if (prefix.equals("R") && !roomMap.containsKey(shortId)) return shortId;

        } while (attempts < 10);

        throw new RuntimeException("Failed to generate unique short ID.");
    }

    // ------------------- PATIENT CHECK-IN -------------------
    public void checkInPatient(Scanner scanner) {
        System.out.println("\n--- Patient Check-In ---");

        List<Doctor> availableDoctors = getAvailableDoctors();
        if (availableDoctors.isEmpty()) {
            System.out.println("!! No doctors available !!");
            return;
        }

        System.out.print("Enter Patient's Name: ");
        String name = scanner.nextLine();

        System.out.print("Enter Patient's Concern: ");
        String concern = scanner.nextLine();

        int priority = -1;
        while (priority < 1 || priority > 5) {
            try {
                System.out.print("Priority Level (1 urgent - 5 least): ");
                priority = Integer.parseInt(scanner.nextLine());
            } catch (Exception ignored) {}
        }

        System.out.println("\nChoose a doctor:");
        for (int i = 0; i < availableDoctors.size(); i++) {
            System.out.println((i + 1) + ". " + availableDoctors.get(i).getName());
        }

        Doctor assigned = null;
        while (assigned == null) {
            try {
                System.out.print("Enter doctor number: ");
                int choice = Integer.parseInt(scanner.nextLine());
                if (choice >= 1 && choice <= availableDoctors.size()) {
                    assigned = availableDoctors.get(choice - 1);
                }
            } catch (Exception ignored) {}
        }

        String patientID = generateShortID("P");
        Patient p = new Patient(patientID, name, concern, priority);
        p.setDoctorID(assigned.getDoctorId());

        waitingQueue.add(p);
        patientNames.put(patientID, name);

        System.out.println("Patient checked in and assigned to Dr. " + assigned.getName());
    }

    // ------------------- TREAT NEXT PATIENT -------------------
    public void treatNextPatient(Scanner scanner) {
        if (waitingQueue.isEmpty()) {
            System.out.println("No patients waiting.");
            return;
        }

        Patient p = waitingQueue.poll();
        Doctor d = doctorMap.get(p.getDoctorID());

        if (!d.isInClinic()) {
            System.out.println("Assigned doctor is busy.");
            waitingQueue.add(p);
            return;
        }

        Room room = findAvailableRoom();
        if (room == null) {
            System.out.println("No rooms available. Patient returned to queue.");
            waitingQueue.add(p);
            return;
        }

        d.setInClinic(false);
        room.setOccupied(true);

        System.out.println("\nTreating: " + p.getName());
        System.out.print("Diagnosis: ");
        String diagnosis = scanner.nextLine();
        System.out.print("Treatment: ");
        String treatment = scanner.nextLine();

        ClinicHistoryRecord record =
                new ClinicHistoryRecord(LocalDateTime.now(), d.getName(), diagnosis, treatment);

        patientHistoryMap.computeIfAbsent(p.getPatientID(), k -> new ArrayList<>())
                .add(record);

        d.setInClinic(true);
        room.setOccupied(false);

        System.out.println("Treatment complete.");
    }

    // ------------------- VIEW HISTORY -------------------
    public void viewAllPatientHistory(Scanner scanner) {
        if (patientNames.isEmpty()) {
            System.out.println("No patients.");
            return;
        }

        List<String> ids = new ArrayList<>(patientNames.keySet());
        for (int i = 0; i < ids.size(); i++) {
            System.out.println((i + 1) + ". " + patientNames.get(ids.get(i)));
        }

        System.out.print("Select patient: ");
        try {
            int choice = Integer.parseInt(scanner.nextLine());
            if (choice < 1 || choice > ids.size()) return;

            String id = ids.get(choice - 1);
            List<ClinicHistoryRecord> records = patientHistoryMap.get(id);

            System.out.println("\n--- History for " + patientNames.get(id) + " ---");

            if (records == null || records.isEmpty()) {
                System.out.println("No history.");
                return;
            }

            for (ClinicHistoryRecord r : records) {
                System.out.println(r + "\n----------------");
            }

        } catch (Exception ignored) {}
    }

    // ------------------- DOCTOR MANAGEMENT -------------------
    public void registerNewDoctor(Scanner scanner) {
        System.out.print("Doctor Name: ");
        String name = scanner.nextLine();

        String id = generateShortID("D");
        doctorMap.put(id, new Doctor(id, name, true));

        System.out.println("Doctor registered.");
    }

    public void viewDoctorStatus(Scanner scanner) {
        if (doctorMap.isEmpty()) {
            System.out.println("No doctors.");
            return;
        }

        List<Doctor> docs = new ArrayList<>(doctorMap.values());
        for (int i = 0; i < docs.size(); i++) {
            System.out.println((i + 1) + ". " + docs.get(i));
        }

        System.out.print("Select doctor: ");
        try {
            int choice = Integer.parseInt(scanner.nextLine());
            if (choice < 1 || choice > docs.size()) return;

            Doctor d = docs.get(choice - 1);
            System.out.println("Current: " + (d.isInClinic() ? "Available" : "Busy"));

            System.out.print("1=Available, 2=Busy: ");
            int status = Integer.parseInt(scanner.nextLine());
            d.setInClinic(status == 1);

        } catch (Exception ignored) {}
    }

    // ------------------- REMOVE DOCTOR -------------------
    public void removeDoctor(Scanner scanner) {
        if (doctorMap.isEmpty()) {
            System.out.println("No doctors to remove.");
            return;
        }

        List<Doctor> doctors = new ArrayList<>(doctorMap.values());
        for (int i = 0; i < doctors.size(); i++) {
            System.out.println((i + 1) + ". " + doctors.get(i).getName());
        }

        System.out.print("Enter number to remove (0 cancel): ");
        try {
            int choice = Integer.parseInt(scanner.nextLine());
            if (choice == 0) return;

            if (choice > 0 && choice <= doctors.size()) {
                Doctor removed = doctors.get(choice - 1);
                doctorMap.remove(removed.getDoctorId());
                System.out.println("Doctor " + removed.getName() + " removed.");
            } else {
                System.out.println("Invalid choice.");
            }
        } catch (Exception e) {
            System.out.println("Invalid input.");
        }
    }

    // ------------------- ROOM MANAGEMENT -------------------
    public void addTreatmentRoom(Scanner scanner) {
        System.out.print("Enter Room Name: ");
        String type = scanner.nextLine();

        String roomID = generateShortID("R");
        roomMap.put(roomID, new Room(roomID, type, false));

        System.out.println("Room " + type + " added.");
    }

    // ------------------- REMOVE ROOM -------------------
    public void removeRoom(Scanner scanner) {
        if (roomMap.isEmpty()) {
            System.out.println("No rooms to remove.");
            return;
        }

        List<Room> rooms = new ArrayList<>(roomMap.values());
        for (int i = 0; i < rooms.size(); i++) {
            System.out.println((i + 1) + ". " + rooms.get(i).getType());
        }

        System.out.print("Enter number to remove (0 cancel): ");
        try {
            int choice = Integer.parseInt(scanner.nextLine());
            if (choice == 0) return;

            if (choice > 0 && choice <= rooms.size()) {
                Room removed = rooms.get(choice - 1);
                roomMap.remove(removed.getRoomID());
                System.out.println("Room " + removed.getType() + " removed.");
            } else {
                System.out.println("Invalid choice.");
            }

        } catch (Exception e) {
            System.out.println("Invalid input.");
        }
    }

    // ------------------- SAVE & LOAD -------------------
    public void exitAndSave() {
        savePatients();
        saveDoctors();
        saveHistory();
        System.out.println("System saved. Goodbye!");
    }

    private void loadPatients() {
        File file = new File(PATIENTS_FILE);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            br.readLine(); // skip header

            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length >= 2) {
                    patientNames.put(p[0], p[1]);
                }
            }
        } catch (IOException ignored) {}
    }

    private void savePatients() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(PATIENTS_FILE))) {
            pw.println("PatientID,Name");

            for (String id : patientNames.keySet()) {
                pw.println(id + "," + patientNames.get(id));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadDoctors() {
        File file = new File(DOCTORS_FILE);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            br.readLine(); // skip header

            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length >= 3) {
                    String id = p[0];
                    String name = p[1];
                    boolean inClinic = Boolean.parseBoolean(p[2]);

                    doctorMap.put(id, new Doctor(id, name, inClinic));
                }
            }
        } catch (IOException ignored) {}
    }

    private void saveDoctors() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(DOCTORS_FILE))) {
            pw.println("DoctorID,Name,InClinic");

            for (Doctor d : doctorMap.values()) {
                pw.println(d.getDoctorId() + "," + d.getName() + "," + d.isInClinic());
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadHistory() {
        File file = new File(HISTORY_FILE);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            br.readLine(); // skip header

            while ((line = br.readLine()) != null) {
                String[] p = line.split(",", -1);
                if (p.length >= 5) {
                    String id = p[0];
                    LocalDateTime time = LocalDateTime.parse(p[1], formatter);
                    ClinicHistoryRecord r = new ClinicHistoryRecord(time, p[2], p[3], p[4]);

                    patientHistoryMap.computeIfAbsent(id, k -> new ArrayList<>()).add(r);
                }
            }
        } catch (IOException ignored) {}
    }

    private void saveHistory() {
        File file = new File(HISTORY_FILE);
        boolean newFile = !file.exists();

        try (PrintWriter pw = new PrintWriter(new FileWriter(file, true))) {

            if (newFile) {
                pw.println("PatientID,DateTime,Doctor,Diagnosis,Treatment");
            }

            for (String id : patientHistoryMap.keySet()) {
                for (ClinicHistoryRecord r : patientHistoryMap.get(id)) {
                    pw.println(
                            id + "," +
                            r.getDateTime().format(formatter) + "," +
                            r.getDoctorName() + "," +
                            r.getDiagnosis().replace(",", ";") + "," +
                            r.getTreatment().replace(",", ";")
                    );
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ------------------- HELPERS -------------------
    private List<Doctor> getAvailableDoctors() {
        List<Doctor> list = new ArrayList<>();
        for (Doctor d : doctorMap.values())
            if (d.isInClinic()) list.add(d);
        return list;
    }

    private Room findAvailableRoom() {
        for (Room r : roomMap.values())
            if (!r.isOccupied()) return r;
        return null;
    }
}
