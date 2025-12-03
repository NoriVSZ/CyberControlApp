package com.example.cybercontrolpanel;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private DatabaseReference mDatabase;
    
    // UI Elements
    private Spinner spinnerComputerId, spinnerAlertComputer, spinnerUserComputer, spinnerUserId, spinnerAlertType;
    private EditText editAlertMessage;
    
    // Computer List
    private List<String> computerList = new ArrayList<>();
    private ArrayAdapter<String> computerAdapter;

    // User List
    private List<String> userList = new ArrayList<>();
    private ArrayAdapter<String> userAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize Firebase
        mDatabase = FirebaseDatabase.getInstance().getReference();

        initializeViews();
        setupSpinners();
        loadComputersFromFirebase();
        loadUsersFromFirebase();
        setupClickListeners();
    }

    private void initializeViews() {
        spinnerComputerId = findViewById(R.id.spinnerComputerId);
        spinnerAlertComputer = findViewById(R.id.spinnerAlertComputer);
        spinnerUserComputer = findViewById(R.id.spinnerUserComputer);
        spinnerUserId = findViewById(R.id.spinnerUserId);
        spinnerAlertType = findViewById(R.id.spinnerAlertType);
        editAlertMessage = findViewById(R.id.editAlertMessage);
    }

    private void setupSpinners() {
        // Alert Types
        String[] alertTypes = {"Información", "Advertencia", "Error", "Éxito"};
        ArrayAdapter<String> alertAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, alertTypes);
        spinnerAlertType.setAdapter(alertAdapter);

        // Computer Adapters (Empty initially)
        computerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, computerList);
        spinnerComputerId.setAdapter(computerAdapter);
        spinnerAlertComputer.setAdapter(computerAdapter);
        spinnerUserComputer.setAdapter(computerAdapter);

        // User Adapter (Empty initially)
        userAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, userList);
        spinnerUserId.setAdapter(userAdapter);
    }

    private void loadComputersFromFirebase() {
        // Path updated to "PC"
        mDatabase.child("PC").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                computerList.clear();
                computerList.add("Seleccionar...");
                for (DataSnapshot data : snapshot.getChildren()) {
                    computerList.add(data.getKey());
                }
                computerAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Error cargando computadores", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadUsersFromFirebase() {
        mDatabase.child("usuarios_registrados").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                userList.clear();
                userList.add("Seleccionar...");
                for (DataSnapshot data : snapshot.getChildren()) {
                    userList.add(data.getKey());
                }
                userAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Error cargando usuarios", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupClickListeners() {
        // Control Individual
        findViewById(R.id.btnComputerOn).setOnClickListener(v -> updateComputerState("Encendido"));
        findViewById(R.id.btnComputerOff).setOnClickListener(v -> updateComputerState("Apagado"));
        findViewById(R.id.btnInternetOn).setOnClickListener(v -> updateComputerInternet("Activo"));
        findViewById(R.id.btnInternetOff).setOnClickListener(v -> updateComputerInternet("Inactivo"));

        // Control Masivo
        findViewById(R.id.btnAllOn).setOnClickListener(v -> updateAllComputersState("Encendido"));
        findViewById(R.id.btnAllOff).setOnClickListener(v -> updateAllComputersState("Apagado"));
        findViewById(R.id.btnAllNetOn).setOnClickListener(v -> updateAllComputersInternet("Activo"));
        findViewById(R.id.btnAllNetOff).setOnClickListener(v -> updateAllComputersInternet("Inactivo"));

        // Alertas
        findViewById(R.id.btnSendAlert).setOnClickListener(v -> sendAlert());

        // Usuarios
        findViewById(R.id.btnAssignUser).setOnClickListener(v -> assignUserToComputer());
        findViewById(R.id.btnRegisterUser).setOnClickListener(v -> showRegisterUserDialog());

        // Logout
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    // --- Logic Methods ---

    private void updateComputerState(String newState) {
        String computerId = getSelectedComputer(spinnerComputerId);
        if (computerId == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("estado", newState);
        if (newState.equals("Apagado")) {
            updates.put("tiempoUso", "00:00:00");
        }

        // Path updated to "PC"
        mDatabase.child("PC").child(computerId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Estado actualizado: " + newState, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error al actualizar", Toast.LENGTH_SHORT).show());
    }

    private void updateComputerInternet(String newState) {
        String computerId = getSelectedComputer(spinnerComputerId);
        if (computerId == null) return;

        // Path updated to "PC"
        mDatabase.child("PC").child(computerId).child("internet").setValue(newState);
    }

    private void updateAllComputersState(String newState) {
        // Path updated to "PC"
        mDatabase.child("PC").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot computer : snapshot.getChildren()) {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("estado", newState);
                    if (newState.equals("Apagado")) {
                        updates.put("tiempoUso", "00:00:00");
                    }
                    computer.getRef().updateChildren(updates);
                }
                Toast.makeText(MainActivity.this, "Todos los computadores " + newState, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void updateAllComputersInternet(String newState) {
        // Path updated to "PC"
        mDatabase.child("PC").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot computer : snapshot.getChildren()) {
                    computer.getRef().child("internet").setValue(newState);
                }
                Toast.makeText(MainActivity.this, "Internet " + newState + " en todos", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void sendAlert() {
        String computerId = getSelectedComputer(spinnerAlertComputer);
        String message = editAlertMessage.getText().toString().trim();
        if (spinnerAlertType.getSelectedItem() == null) return;
        String type = spinnerAlertType.getSelectedItem().toString();

        if (computerId == null) return;
        if (TextUtils.isEmpty(message)) {
            editAlertMessage.setError("Escribe un mensaje");
            return;
        }

        // Path updated to "PC"
        mDatabase.child("PC").child(computerId).child("estado").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String state = snapshot.getValue(String.class);
                if ("Encendido".equals(state)) {
                    Map<String, Object> alert = new HashMap<>();
                    alert.put("mensaje", message);
                    alert.put("tipo", type);
                    alert.put("timestamp", System.currentTimeMillis());

                    mDatabase.child("PC").child(computerId).child("alertas").push().setValue(alert);
                    Toast.makeText(MainActivity.this, "Alerta enviada a " + computerId, Toast.LENGTH_SHORT).show();
                    editAlertMessage.setText("");
                } else {
                    // Fixed Toast.LENGTH_WARNING -> Toast.LENGTH_LONG
                    Toast.makeText(MainActivity.this, "El computador debe estar encendido", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    private void assignUserToComputer() {
        String computerId = getSelectedComputer(spinnerUserComputer);
        String userId = getSelectedComputer(spinnerUserId); 

        if (computerId == null || userId == null) return;

        mDatabase.child("usuarios_registrados").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Path updated to "PC"
                    mDatabase.child("PC").child(computerId).child("usuario").setValue(userId);
                    Toast.makeText(MainActivity.this, "Usuario " + userId + " asignado a " + computerId, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Usuario no válido", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }
    
    private void showRegisterUserDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_register_user, null);
        builder.setView(dialogView);

        TextInputEditText editId = dialogView.findViewById(R.id.dialogEditId);
        TextInputEditText editName = dialogView.findViewById(R.id.dialogEditName);
        TextInputEditText editEmail = dialogView.findViewById(R.id.dialogEditEmail);
        Button btnSave = dialogView.findViewById(R.id.dialogBtnSave);

        AlertDialog dialog = builder.create();
        
        btnSave.setOnClickListener(v -> {
            String id = editId.getText().toString().trim();
            String name = editName.getText().toString().trim();
            String email = editEmail.getText().toString().trim();

            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(name)) {
                Toast.makeText(this, "ID y Nombre son obligatorios", Toast.LENGTH_SHORT).show();
                return;
            }

            // Check if ID exists
            mDatabase.child("usuarios_registrados").child(id).addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()) {
                        editId.setError("Este ID ya existe");
                    } else {
                        Map<String, String> userData = new HashMap<>();
                        userData.put("nombre", name);
                        userData.put("correo", email);
                        
                        mDatabase.child("usuarios_registrados").child(id).setValue(userData);
                        Toast.makeText(MainActivity.this, "Usuario registrado exitosamente", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) { }
            });
        });

        dialog.show();
    }

    private String getSelectedComputer(Spinner spinner) {
        if (spinner.getSelectedItem() == null || spinner.getSelectedItemPosition() == 0) {
            Toast.makeText(this, "Selecciona una opción válida", Toast.LENGTH_SHORT).show();
            return null;
        }
        return spinner.getSelectedItem().toString();
    }
}