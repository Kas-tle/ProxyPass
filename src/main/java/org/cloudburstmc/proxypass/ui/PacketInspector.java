package org.cloudburstmc.proxypass.ui;

import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.proxypass.ProxyPass;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketInspector extends Application {
    private final ObservableList<CapturedPacket> packets = FXCollections.observableArrayList();
    private final FilteredList<CapturedPacket> filteredPackets = new FilteredList<>(packets);
    private static final Queue<CapturedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());
    
    private TableView<CapturedPacket> packetTable;
    private TextField filterField;
    private CodeArea hexDumpArea;
    private TreeView<String> packetStructureTree;
    private CodeArea packetDetailsArea;
    
    private static volatile PacketInspector instance;
    private static volatile boolean initialized = false;
    
    public static PacketInspector getInstance() {
        return instance;
    }
    
    @Override
    public void start(Stage primaryStage) {
        instance = this;
        initialized = true;

        PacketFieldInspector.initializePacketFields(ProxyPass.CODEC);

        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        
        createPacketTable();
        createHexDumpArea();
        createPacketDetailsArea();
        createPacketStructureTree();
        
        filterField = new TextField();
        filterField.setPromptText("Filter packets (e.g. 'ClientBound' or 'LoginPacket')");
        filterField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredPackets.setPredicate(packet -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                
                String lowerCaseFilter = newValue.toLowerCase();
                
                return packet.getDirection().toLowerCase().contains(lowerCaseFilter) ||
                       packet.getPacketType().toLowerCase().contains(lowerCaseFilter) ||
                       packet.getSourceAddress().toLowerCase().contains(lowerCaseFilter);
            });
        });
        
        SplitPane mainSplitPane = new SplitPane();
        
        VBox topContainer = new VBox(5);
        topContainer.setPadding(new Insets(10));
        topContainer.getChildren().addAll(new Label("Filter:"), filterField, packetTable);
        
        SplitPane detailSplitPane = new SplitPane();
        detailSplitPane.setOrientation(Orientation.HORIZONTAL);
        
        VBox hexDumpContainer = new VBox(5);
        hexDumpContainer.setPadding(new Insets(10));
        hexDumpContainer.getChildren().addAll(new Label("Hex Dump:"), hexDumpArea);
        VBox.setVgrow(hexDumpArea, Priority.ALWAYS);
        
        SplitPane rightDetailPane = new SplitPane();
        rightDetailPane.setOrientation(Orientation.VERTICAL);
        
        VBox structureContainer = new VBox(5);
        structureContainer.setPadding(new Insets(10));
        structureContainer.getChildren().addAll(new Label("Packet Structure:"), packetStructureTree);
        VBox.setVgrow(packetStructureTree, Priority.ALWAYS);
        
        VBox detailsContainer = new VBox(5);
        detailsContainer.setPadding(new Insets(10));
        detailsContainer.getChildren().addAll(new Label("BedrockPacket#toString:"), packetDetailsArea);
        VBox.setVgrow(packetDetailsArea, Priority.ALWAYS);
        
        rightDetailPane.getItems().addAll(structureContainer, detailsContainer);
        detailSplitPane.getItems().addAll(hexDumpContainer, rightDetailPane);
        
        mainSplitPane.getItems().addAll(topContainer, detailSplitPane);
        mainSplitPane.setDividerPosition(0, 0.4);
        
        Button clearButton = new Button("Clear");
        clearButton.setOnAction(e -> clearPackets());
        
        Button pauseButton = new Button("Pause Capture");
        final boolean[] paused = {false};
        pauseButton.setOnAction(e -> {
            paused[0] = !paused[0];
            pauseButton.setText(paused[0] ? "Resume Capture" : "Pause Capture");
        });
        
        HBox buttonBar = new HBox(10);
        buttonBar.setPadding(new Insets(10));
        buttonBar.getChildren().addAll(clearButton, pauseButton);
        
        BorderPane root = new BorderPane();
        root.setCenter(mainSplitPane);
        root.setBottom(buttonBar);
        
        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setTitle("ProxyPass");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        Thread updateThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(100);
                    
                    if (!paused[0]) {
                        List<CapturedPacket> newPackets = new ArrayList<>();
                        while (!packetQueue.isEmpty()) {
                            newPackets.add(packetQueue.poll());
                        }
                        
                        if (!newPackets.isEmpty()) {
                            Platform.runLater(() -> packets.addAll(newPackets));
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        updateThread.setDaemon(true);
        updateThread.start();
    }
    
    @SuppressWarnings("unchecked")
    private void createPacketTable() {
        packetTable = new TableView<>(filteredPackets);
        
        TableColumn<CapturedPacket, String> timeCol = new TableColumn<>("Time");
        timeCol.setCellValueFactory(data -> new SimpleStringProperty(
                formatter.format(data.getValue().getTimestamp())));
        
        TableColumn<CapturedPacket, String> directionCol = new TableColumn<>("Direction");
        directionCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDirection()));
        
        TableColumn<CapturedPacket, String> sourceCol = new TableColumn<>("Source");
        sourceCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSourceAddress()));
        
        TableColumn<CapturedPacket, String> packetTypeCol = new TableColumn<>("Packet Type");
        packetTypeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPacketType()));
        
        TableColumn<CapturedPacket, String> lengthCol = new TableColumn<>("Length");
        lengthCol.setCellValueFactory(data -> new SimpleStringProperty(
                String.valueOf(data.getValue().getPacketData().length)));
        
        packetTable.getColumns().addAll(timeCol, directionCol, sourceCol, packetTypeCol, lengthCol);
        
        packetTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(packetTable, Priority.ALWAYS);
        
        packetTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        updateHexDump(newValue);
                        updatePacketDetails(newValue);
                        updatePacketStructure(newValue);
                    }
                });
    }
    
    private void createHexDumpArea() {
        hexDumpArea = new CodeArea();
        hexDumpArea.setEditable(false);
        hexDumpArea.getStylesheets().add("-fx-font-family: monospace;");
        VBox.setVgrow(hexDumpArea, Priority.ALWAYS);
    }
    
    private void createPacketDetailsArea() {
        packetDetailsArea = new CodeArea();
        packetDetailsArea.setEditable(false);
        packetDetailsArea.getStylesheets().add("-fx-font-family: monospace;");
        VBox.setVgrow(packetDetailsArea, Priority.ALWAYS);
    }
    
    private void createPacketStructureTree() {
        packetStructureTree = new TreeView<>();
        packetStructureTree.setRoot(new TreeItem<>("No packet selected"));
        packetStructureTree.setCellFactory(new Callback<>() {
            @Override
            public TreeCell<String> call(TreeView<String> param) {
                return new TreeCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                        } else {
                            setText(item);
                            setOnMouseClicked(event -> {
                                // TODO: Implement logic to highlight the corresponding hex range
                            });
                        }
                    }
                };
            }
        });
        VBox.setVgrow(packetStructureTree, Priority.ALWAYS);
    }
    
    private void updateHexDump(CapturedPacket packet) {
        byte[] data = packet.getPacketData();
        StringBuilder hexDump = new StringBuilder();
        StringBuilder asciiDump = new StringBuilder();
        int offset = 0;
        
        while (offset < data.length) {
            // Address column
            hexDump.append(String.format("%08X  ", offset));
            
            // Hex columns (16 bytes per row)
            for (int i = 0; i < 16; i++) {
                if (offset + i < data.length) {
                    hexDump.append(String.format("%02X ", data[offset + i]));
                    
                    // ASCII representation
                    char c = (char) data[offset + i];
                    if (c >= 32 && c < 127) {
                        asciiDump.append(c);
                    } else {
                        asciiDump.append('.');
                    }
                } else {
                    hexDump.append("   ");
                    asciiDump.append(' ');
                }
                
                // Add extra space after 8 bytes
                if (i == 7) {
                    hexDump.append(" ");
                }
            }
            
            // Append ASCII dump
            hexDump.append(" |").append(asciiDump).append("|\n");
            asciiDump.setLength(0);
            offset += 16;
        }
        
        hexDumpArea.replaceText(hexDump.toString());
    }
    
    private void updatePacketDetails(CapturedPacket packet) {
        packetDetailsArea.replaceText(packet.getPacketDetails());
    }
    
    private void updatePacketStructure(CapturedPacket packet) {
        // Use the automated packet structure generator
        if (packet.getOriginalPacket() != null) {
            TreeItem<String> root = PacketFieldInspector.buildPacketStructure(
                    packet.getPacketType(), packet.getOriginalPacket());
            packetStructureTree.setRoot(root);
        } else {
            // Fallback for packets without the original instance
            TreeItem<String> root = new TreeItem<>(packet.getPacketType());
            root.setExpanded(true);
            root.getChildren().add(new TreeItem<>("Packet data not available for inspection"));
            packetStructureTree.setRoot(root);
        }
    }
    
    private void highlightHexRange(HexRange range) {
        String text = hexDumpArea.getText();
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    }
    
    private void clearPackets() {
        packets.clear();
        packetQueue.clear();
    }
    
    public static void addPacket(BedrockPacket packet, byte[] data, boolean clientBound, String sourceAddress) {
        CapturedPacket capturedPacket = new CapturedPacket(
                Instant.now(),
                clientBound ? "CLIENT_BOUND" : "SERVER_BOUND",
                sourceAddress,
                packet.getClass().getSimpleName(),
                data,
                packet.toString(),
                packet // Store the original packet
        );
        
        packetQueue.add(capturedPacket);
    }
    
    public static void launchUI() {
        new Thread(() -> {
            System.out.println("Starting JavaFX application");
            launch(new String[0]);
        }).start();
        
        System.out.println("Waiting for JavaFX to initialize...");
        long startTime = System.currentTimeMillis();
        while (!initialized && System.currentTimeMillis() - startTime < 10000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        if (!initialized) {
            System.out.println("WARNING: JavaFX failed to initialize in a timely manner!");
        } else {
            System.out.println("JavaFX initialized successfully");
        }
    }
    
    public static class CapturedPacket {
        private final Instant timestamp;
        private final String direction;
        private final String sourceAddress;
        private final String packetType;
        private final byte[] packetData;
        private final String packetDetails;
        private final BedrockPacket originalPacket;
        
        public CapturedPacket(Instant timestamp, String direction, String sourceAddress, 
                             String packetType, byte[] packetData, String packetDetails,
                             BedrockPacket originalPacket) {
            this.timestamp = timestamp;
            this.direction = direction;
            this.sourceAddress = sourceAddress;
            this.packetType = packetType;
            this.packetData = packetData;
            this.packetDetails = packetDetails;
            this.originalPacket = originalPacket;
        }
        
        public BedrockPacket getOriginalPacket() {
            return originalPacket;
        }

        public Instant getTimestamp() {
            return timestamp;
        }
        
        public String getDirection() {
            return direction;
        }
        
        public String getSourceAddress() {
            return sourceAddress;
        }
        
        public String getPacketType() {
            return packetType;
        }
        
        public byte[] getPacketData() {
            return packetData;
        }
        
        public String getPacketDetails() {
            return packetDetails;
        }
    }
    
    private static class HexRange {
        private final int start;
        private final int end;
        
        public HexRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
        
        public int getStart() {
            return start;
        }
        
        public int getEnd() {
            return end;
        }
    }
}
