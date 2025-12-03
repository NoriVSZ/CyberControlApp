package com.example.cybercontrolpanel;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
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

// imports de Hivemq
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;



public class MainActivity extends AppCompatActivity {

    private DatabaseReference mDatabase;
    
    // UI Elements
    private Spinner spinnerComputerId, spinnerAlertComputer, spinnerUserComputer, spinnerUserId, spinnerAlertType;
    private EditText editAlertMessage;
    
    // Computer List
    private final List<String> computerList = new ArrayList<>();
    private ArrayAdapter<String> computerAdapter;

    // User List
    private final List<String> userList = new ArrayList<>();
    private ArrayAdapter<String> userAdapter;

    // Hivemq configuration (se deben cambiar para ejecutar con tu cluster)
    private Mqtt3AsyncClient mqttClient;
    private static final String HIVE_MQ_HOST = "tucluster.url"; // dato aleatorio para subir a github
    private static final int HIVE_MQ_PORT = 8883; // puerto comun para HiveMQ
    private static final String HIVE_MQ_USER = "tuuser.cluster"; // dato aleatorio para subir a github
    private static final String HIVE_MQ_PASS = "tucontraseña.cluster"; // dato aleatorio para subir a github


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

        // connect to hivemq cloud
        connectToHiveMQ();
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

    // HiveMQ connection
    private void connectToHiveMQ() {
        mqttClient = MqttClient.builder()
                .useMqttVersion3()
                .identifier("CyberAdmin_" + System.currentTimeMillis())
                .serverHost(HIVE_MQ_HOST)
                .serverPort(HIVE_MQ_PORT)
                .sslWithDefaultConfig()
                .buildAsync();

        mqttClient.connectWith()
                .simpleAuth()
                .username(HIVE_MQ_USER)
                .password(HIVE_MQ_PASS.getBytes())
                .applySimpleAuth()
                .send()
                .whenComplete((connAck, throwable) -> {
                    if (throwable != null) {
                        Log.e("HiveMQ", "Error al conectar con HiveMQ Cloud", throwable);
                    } else {
                        Log.i("HiveMQ", "Conectado a HiveMQ Cloud");
                    }
                });


    }

    // Send command to HiveMQ
    private void sendCommandToHiveMQ(String pcId, String action, Map<String, Object> extraData) {
        if (mqttClient == null || mqttClient.getState() != MqttClientState.CONNECTED) {
            Toast.makeText(this, "No conectado a HiveMQ", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder payload = new StringBuilder();
        payload.append("{\"pcId\":\"").append(pcId).append("\",\"action\":\"").append(action).append("\"");

        if (extraData != null && !extraData.isEmpty()) {
            payload.append(",\"datos\":{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : extraData.entrySet()) {
                if (!first) payload.append(",");
                payload.append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue()).append("\"");
                first = false;
            }
            payload.append("}");
        }
        payload.append("}");

        String topic = "Cyber/comando/" + pcId;
        mqttClient.publishWith()
                .topic(topic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .payload(payload.toString().getBytes())
                .send()
                .whenComplete((result, error) -> {
                    if (error != null) {
                        Log.e("HiveMQ", "Error al enviar comando a HiveMQ", error);
                    } else {
                        Log.i("HiveMQ", "Comando enviado a HiveMQ");
                    }
                });
    }

    // --- Logic Methods adaptados a hivemq + firebase directo---

    private void updateComputerState(String newState) {
        String computerId = getSelectedComputer(spinnerComputerId);
        if (computerId == null) return;

        // 1. Enviar MQTT
        sendCommandToHiveMQ(computerId, "estado", Map.of("valor", newState));

        // 2. Actualizar Firebase Directamente
        Map<String, Object> updates = new HashMap<>();
        updates.put("estado", newState);
        
        if ("Apagado".equals(newState)) {
            Map<String, Object> resetData = new HashMap<>();
            resetData.put("usuario", "Sin usuario");
            resetData.put("tiempoUso", "00:00:00");
            sendCommandToHiveMQ(computerId, "reset", resetData);

            // Actualizar tambien en Firebase
            updates.put("tiempoUso", "00:00:00");
            updates.put("usuario", "Sin usuario");
        }

        mDatabase.child("PC").child(computerId).updateChildren(updates)
            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Estado actualizado (FB+MQTT): " + newState, Toast.LENGTH_SHORT).show())
            .addOnFailureListener(e -> Toast.makeText(this, "Error al actualizar Firebase", Toast.LENGTH_SHORT).show());
    }

    private void updateComputerInternet(String newState) {
        String computerId = getSelectedComputer(spinnerComputerId);
        if (computerId == null) return;

        // 1. Enviar MQTT
        sendCommandToHiveMQ(computerId, "internet", Map.of("valor", newState));

        // 2. Actualizar Firebase Directamente
        mDatabase.child("PC").child(computerId).child("internet").setValue(newState)
             .addOnSuccessListener(aVoid -> Toast.makeText(this, "Internet " + newState + " (FB+MQTT)", Toast.LENGTH_SHORT).show());
    }

    private void updateAllComputersState(String newState) {
        mDatabase.child("PC").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot computer : snapshot.getChildren()) {
                    String pcId = computer.getKey();
                    
                    // 1. Enviar MQTT
                    sendCommandToHiveMQ(pcId, "estado", Map.of("valor", newState));
                    
                    // 2. Actualizar Firebase
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("estado", newState);

                    if ("Apagado".equals(newState)) {
                        Map<String, Object> resetData = new HashMap<>();
                        resetData.put("usuario", "Sin usuario");
                        resetData.put("tiempoUso", "00:00:00");
                        sendCommandToHiveMQ(pcId, "reset", resetData);
                        
                        updates.put("tiempoUso", "00:00:00");
                        updates.put("usuario", "Sin usuario");
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
        mDatabase.child("PC").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot computer : snapshot.getChildren()) {
                    String pcId = computer.getKey();
                    // 1. Enviar MQTT
                    sendCommandToHiveMQ(pcId, "internet", Map.of("valor", newState));
                    // 2. Actualizar Firebase
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

        Toast.makeText(this, "Funcionalidad de alerta pospuesta (Monitor Push)", Toast.LENGTH_SHORT).show();

        /* codigo temporalmente comentado para buscar otro enfoque
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
                    Toast.makeText(MainActivity.this, "El computador debe estar encendido", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
        */
    }

    private void assignUserToComputer() {
        String computerId = getSelectedComputer(spinnerUserComputer);
        String userId = getSelectedComputer(spinnerUserId); 

        if (computerId == null || userId == null) return;
        
        // Verificar estado de pc antes de asignar user
        mDatabase.child("PC").child(computerId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String estado = snapshot.child("estado").getValue(String.class);
                    String usuarioActual = snapshot.child("usuario").getValue(String.class);
                    
                    // Logica: Computador debe estar Encendido y su valor de usuario debe ser "Sin usuario"
                    if ("Encendido".equals(estado)) {
                        if ("Sin usuario".equals(usuarioActual) || usuarioActual == null) {
                            // verifica usuario y asigna
                            checkUserAndAssign(computerId, userId);
                        } else {
                            Toast.makeText(MainActivity.this, "El computador ya tiene un usuario asignado", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "El computador debe estar encendido para asignar usuario", Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }
    
    private void checkUserAndAssign(String computerId, String userId) {
        // Primero verificamos que el usuario exista en registros
        mDatabase.child("usuarios_registrados").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot userSnapshot) {
                if (userSnapshot.exists()) {
                    // Usuario existe. AHORA verificamos si ya está activo en otro PC.
                    mDatabase.child("PC").orderByChild("usuario").equalTo(userId).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot pcSnapshot) {
                            if (pcSnapshot.exists() && pcSnapshot.getChildrenCount() > 0) {
                                // Si la query devuelve resultados, significa que este usuario ya está en algun PC
                                Toast.makeText(MainActivity.this, "Este usuario ya está activo en otro equipo", Toast.LENGTH_LONG).show();
                            } else {
                                // No está en uso, procedemos a asignar
                                mDatabase.child("PC").child(computerId).child("usuario").setValue(userId);
                                Toast.makeText(MainActivity.this, "Usuario " + userId + " asignado a " + computerId, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(MainActivity.this, "Error al verificar disponibilidad del usuario", Toast.LENGTH_SHORT).show();
                        }
                    });
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
            String id = editId.getText() != null ? editId.getText().toString().trim() : "";
            String name = editName.getText() != null ? editName.getText().toString().trim() : "";
            String email = editEmail.getText() != null ? editEmail.getText().toString().trim() : "";

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
