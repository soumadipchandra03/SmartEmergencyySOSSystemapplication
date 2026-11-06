package com.example.smartenergysos;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERMISSIONS = 100;

    // Screens
    View layoutHome, layoutContacts, layoutMedical, layoutHistory;

    // Home
    Button btnSOS, btnCall, btnContacts, btnMedical, btnHistory;

    // Contacts
    EditText etName, etPhone;
    Button btnSaveContact, btnBackContacts;
    ListView listContacts;
    List<String[]> contacts;

    // Medical
    EditText etBlood, etAllergies, etConditions, etMedicines;
    Button btnSaveMedical, btnBackMedical;

    // History
    ListView listHistory;
    TextView tvEmpty;
    Button btnClearHistory, btnBackHistory;

    Storage storage;
    CountDownTimer timer;
    AlertDialog dialog;
    OnBackPressedCallback backCallback;

    // =====================================================
    // Storage (SharedPreferences helper)
    // =====================================================
    static class Storage {
        private final SharedPreferences sp;

        Storage(Context c) {
            sp = c.getSharedPreferences("sos_prefs", Context.MODE_PRIVATE);
        }

        List<String[]> getContacts() {
            List<String[]> list = new ArrayList<>();
            try {
                JSONArray arr = new JSONArray(sp.getString("contacts", "[]"));
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    list.add(new String[]{o.getString("name"), o.getString("phone")});
                }
            } catch (JSONException ignored) {
            }
            return list;
        }

        void saveContacts(List<String[]> list) {
            JSONArray arr = new JSONArray();
            try {
                for (String[] c : list) {
                    JSONObject o = new JSONObject();
                    o.put("name", c[0]);
                    o.put("phone", c[1]);
                    arr.put(o);
                }
            } catch (JSONException ignored) {
            }
            sp.edit().putString("contacts", arr.toString()).apply();
        }

        void saveMedical(String blood, String allergies, String conditions, String medicines) {
            sp.edit()
                    .putString("blood", blood)
                    .putString("allergies", allergies)
                    .putString("conditions", conditions)
                    .putString("medicines", medicines)
                    .apply();
        }

        String getMedical(String key) {
            return sp.getString(key, "");
        }

        List<String> getHistory() {
            List<String> list = new ArrayList<>();
            try {
                JSONArray arr = new JSONArray(sp.getString("history", "[]"));
                for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
            } catch (JSONException ignored) {
            }
            return list;
        }

        void addHistory(String entry) {
            List<String> list = getHistory();
            list.add(0, entry);
            saveHistory(list);
        }

        void clearHistory() {
            saveHistory(new ArrayList<>());
        }

        private void saveHistory(List<String> list) {
            JSONArray arr = new JSONArray();
            for (String s : list) arr.put(s);
            sp.edit().putString("history", arr.toString()).apply();
        }
    }

    // =====================================================
    // onCreate
    // =====================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        storage = new Storage(this);

        // Screens
        layoutHome = findViewById(R.id.layoutHome);
        layoutContacts = findViewById(R.id.layoutContacts);
        layoutMedical = findViewById(R.id.layoutMedical);
        layoutHistory = findViewById(R.id.layoutHistory);

        // Home
        btnSOS = findViewById(R.id.btnSOS);
        btnCall = findViewById(R.id.btnCall);
        btnContacts = findViewById(R.id.btnContacts);
        btnMedical = findViewById(R.id.btnMedical);
        btnHistory = findViewById(R.id.btnHistory);

        // Contacts
        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        btnSaveContact = findViewById(R.id.btnSaveContact);
        btnBackContacts = findViewById(R.id.btnBackContacts);
        listContacts = findViewById(R.id.listContacts);

        // Medical
        etBlood = findViewById(R.id.etBlood);
        etAllergies = findViewById(R.id.etAllergies);
        etConditions = findViewById(R.id.etConditions);
        etMedicines = findViewById(R.id.etMedicines);
        btnSaveMedical = findViewById(R.id.btnSaveMedical);
        btnBackMedical = findViewById(R.id.btnBackMedical);

        // History
        listHistory = findViewById(R.id.listHistory);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnClearHistory = findViewById(R.id.btnClearHistory);
        btnBackHistory = findViewById(R.id.btnBackHistory);

        // Back button: return to home from any sub-screen
        backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                showScreen(layoutHome);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);

        // ----- Home buttons -----
        btnSOS.setOnClickListener(v -> startSOS());

        btnCall.setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))));

        btnContacts.setOnClickListener(v -> {
            refreshContacts();
            showScreen(layoutContacts);
        });

        btnMedical.setOnClickListener(v -> {
            etBlood.setText(storage.getMedical("blood"));
            etAllergies.setText(storage.getMedical("allergies"));
            etConditions.setText(storage.getMedical("conditions"));
            etMedicines.setText(storage.getMedical("medicines"));
            showScreen(layoutMedical);
        });

        btnHistory.setOnClickListener(v -> {
            loadHistory();
            showScreen(layoutHistory);
        });

        // ----- Back buttons -----
        btnBackContacts.setOnClickListener(v -> showScreen(layoutHome));
        btnBackMedical.setOnClickListener(v -> showScreen(layoutHome));
        btnBackHistory.setOnClickListener(v -> showScreen(layoutHome));

        // ----- Contacts logic -----
        btnSaveContact.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String phone = etPhone.getText().toString().trim();

            if (name.isEmpty() || phone.length() < 5) {
                Toast.makeText(this, "Enter a valid name and phone number",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            contacts.add(new String[]{name, phone});
            storage.saveContacts(contacts);
            etName.setText("");
            etPhone.setText("");
            refreshContacts();
            Toast.makeText(this, "Contact saved", Toast.LENGTH_SHORT).show();
        });

        listContacts.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete contact?")
                    .setMessage(contacts.get(position)[0])
                    .setPositiveButton("Delete", (d, w) -> {
                        contacts.remove(position);
                        storage.saveContacts(contacts);
                        refreshContacts();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        // ----- Medical logic -----
        btnSaveMedical.setOnClickListener(v -> {
            storage.saveMedical(
                    etBlood.getText().toString().trim(),
                    etAllergies.getText().toString().trim(),
                    etConditions.getText().toString().trim(),
                    etMedicines.getText().toString().trim());
            Toast.makeText(this, "Medical info saved", Toast.LENGTH_SHORT).show();
            showScreen(layoutHome);
        });

        // ----- History logic -----
        btnClearHistory.setOnClickListener(v -> {
            storage.clearHistory();
            loadHistory();
        });
    }

    // =====================================================
    // Screen switching
    // =====================================================
    private void showScreen(View target) {
        layoutHome.setVisibility(View.GONE);
        layoutContacts.setVisibility(View.GONE);
        layoutMedical.setVisibility(View.GONE);
        layoutHistory.setVisibility(View.GONE);
        target.setVisibility(View.VISIBLE);
        backCallback.setEnabled(target != layoutHome);
    }

    private void refreshContacts() {
        contacts = storage.getContacts();
        List<String> display = new ArrayList<>();
        for (String[] c : contacts) display.add(c[0] + "\n" + c[1]);
        listContacts.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, display));
    }

    private void loadHistory() {
        List<String> history = storage.getHistory();
        listHistory.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, history));
        tvEmpty.setVisibility(history.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // =====================================================
    // SOS flow
    // =====================================================
    private void startSOS() {
        if (storage.getContacts().isEmpty()) {
            Toast.makeText(this, "Please add an emergency contact first",
                    Toast.LENGTH_LONG).show();
            refreshContacts();
            showScreen(layoutContacts);
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_PERMISSIONS);
            return;
        }

        showCountdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                    == PackageManager.PERMISSION_GRANTED) {
                showCountdown();
            } else {
                Toast.makeText(this, "SMS permission is required to send SOS",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showCountdown() {
        dialog = new AlertDialog.Builder(this)
                .setTitle("Sending SOS")
                .setMessage("Sending in 5 seconds...")
                .setCancelable(false)
                .setNegativeButton("CANCEL", (d, w) -> {
                    if (timer != null) timer.cancel();
                    Toast.makeText(this, "SOS cancelled", Toast.LENGTH_SHORT).show();
                })
                .create();
        dialog.show();

        timer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long ms) {
                dialog.setMessage("Sending in " + Math.round(ms / 1000.0) + " seconds...");
            }

            @Override
            public void onFinish() {
                dialog.dismiss();
                sendSOS();
            }
        }.start();
    }

    @SuppressLint("MissingPermission")
    private Location getLocation() {
        boolean fine = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine && !coarse) return null;

        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        Location best = null;
        for (String provider : lm.getProviders(true)) {
            Location l = lm.getLastKnownLocation(provider);
            if (l != null && (best == null || l.getAccuracy() < best.getAccuracy())) {
                best = l;
            }
        }
        return best;
    }

    private void sendSOS() {
        Location loc = getLocation();
        String locationText = (loc != null)
                ? "https://maps.google.com/?q=" + loc.getLatitude() + "," + loc.getLongitude()
                : "Location unavailable";

        StringBuilder msg = new StringBuilder("EMERGENCY! I need help.\nLocation: " + locationText);

        String blood = storage.getMedical("blood");
        String allergies = storage.getMedical("allergies");
        String conditions = storage.getMedical("conditions");
        String medicines = storage.getMedical("medicines");
        if (!blood.isEmpty()) msg.append("\nBlood group: ").append(blood);
        if (!allergies.isEmpty()) msg.append("\nAllergies: ").append(allergies);
        if (!conditions.isEmpty()) msg.append("\nConditions: ").append(conditions);
        if (!medicines.isEmpty()) msg.append("\nMedicines: ").append(medicines);

        int sent = 0;
        try {
            SmsManager sms = (Build.VERSION.SDK_INT >= 31)
                    ? getSystemService(SmsManager.class)
                    : SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(msg.toString());

            for (String[] c : storage.getContacts()) {
                try {
                    sms.sendMultipartTextMessage(c[1], null, parts, null, null);
                    sent++;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to send SMS: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }

        String time = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(new Date());
        storage.addHistory(time + "\n" + locationText + "\nSent to " + sent + " contact(s)");

        Toast.makeText(this, "SOS sent to " + sent + " contact(s)", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) timer.cancel();
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }
}