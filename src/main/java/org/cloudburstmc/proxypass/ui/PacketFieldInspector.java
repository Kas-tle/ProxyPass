package org.cloudburstmc.proxypass.ui;

import javafx.scene.control.TreeItem;
import lombok.Data;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PacketFieldInspector {
    private static final Pattern GETTER_PATTERN = Pattern.compile("^(get|is)([A-Z].*)$");
    private static final Map<Class<? extends BedrockPacket>, List<PacketField>> PACKET_FIELDS = new ConcurrentHashMap<>();
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    /**
     * Initializes the packet field information for all packet types in the codec
     * @param codec The BedrockCodec containing packet definitions
     */
    public static void initializePacketFields(BedrockCodec codec) {
        // Clear existing mappings
        PACKET_FIELDS.clear();
        
        // Iterate through all packet IDs to find all packet types
        for (int i = 0; i < codec.getPacketsByIdLength(); i++) {
            BedrockPacketDefinition<?> definition = codec.getPacketDefinition(i);
            if (definition == null) continue;
            
            try {
                BedrockPacket packet = definition.getFactory().get();
                Class<? extends BedrockPacket> packetClass = packet.getClass();
                analyzePacketClass(packetClass);
            } catch (Exception e) {
                // Skip this packet if there's an issue
            }
        }
    }
    
    /**
     * Analyzes a packet class using reflection to extract field information
     * @param packetClass Class to analyze
     */
    private static void analyzePacketClass(Class<? extends BedrockPacket> packetClass) {
        if (PACKET_FIELDS.containsKey(packetClass)) {
            return;
        }
        
        List<PacketField> fields = new ArrayList<>();

        Map<String, Field> classFields = Arrays.asList(packetClass.getDeclaredFields())
                .stream()
                .collect(Collectors.toMap(Field::getName, field -> field));
        
        for (Method method : packetClass.getMethods()) {
            try {
                String foundFieldName;
                if (method.getName().startsWith("get")) {
                    foundFieldName = method.getName().substring(3);
                } else if (method.getName().startsWith("is")) {
                    foundFieldName = method.getName().substring(2);
                } else {
                    continue;
                }
                char c[] = foundFieldName.toCharArray();
                c[0] = Character.toLowerCase(c[0]);
                foundFieldName = new String(c);

                Field field = classFields.get(foundFieldName);
                if (field == null) continue;
                
                String fieldName = field.getName();
                Class<?> returnType = method.getReturnType();
                
                MethodHandle methodHandle = LOOKUP.unreflect(method);
                
                fields.add(new PacketField(fieldName, returnType, methodHandle));
            } catch (Exception e) {
                continue;
            }
        }
        
        fields.sort(Comparator.comparing(PacketField::getName));
        
        PACKET_FIELDS.put(packetClass, fields);
    }
    
    /**
     * Builds a TreeView structure for the provided packet
     * @param packetName The name of the packet
     * @param packet The packet instance
     * @return A TreeItem representing the root of the packet structure
     */
    public static TreeItem<String> buildPacketStructure(String packetName, BedrockPacket packet) {
        TreeItem<String> root = new TreeItem<>(packetName);
        root.setExpanded(true);
        
        if (packet == null) {
            TreeItem<String> emptyNode = new TreeItem<>("No data available");
            root.getChildren().add(emptyNode);
            return root;
        }
        
        Class<? extends BedrockPacket> packetClass = packet.getClass();
        List<PacketField> fields = PACKET_FIELDS.get(packetClass);
        
        if (fields == null) {
            analyzePacketClass(packetClass);
            fields = PACKET_FIELDS.getOrDefault(packetClass, Collections.emptyList());
        }
        
        for (PacketField field : fields) {
            try {
                Object value = field.getMethodHandle().invoke(packet);
                String displayValue = formatValue(value);
                
                TreeItem<String> fieldNode = new TreeItem<>(field.getName() + ": " + displayValue);
                
                if (value != null && !isPrimitiveOrString(field.getType())) {
                    addComplexFieldChildren(fieldNode, value);
                }
                
                // fieldNode.setUserData(new PacketInspector.HexRange(fieldStart, fieldEnd));
                
                root.getChildren().add(fieldNode);
            } catch (Throwable e) {
                TreeItem<String> errorNode = new TreeItem<>(field.getName() + ": <error reading value>");
                root.getChildren().add(errorNode);
            }
        }
        
        return root;
    }
    
    /**
     * Adds child nodes for complex field types (collections, arrays, nested objects)
     */
    private static void addComplexFieldChildren(TreeItem<String> parent, Object value) {
        if (value == null) return;
        
        // Handle collections
        if (value instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) value;
            int index = 0;
            for (Object item : collection) {
                String itemValue = formatValue(item);
                TreeItem<String> itemNode = new TreeItem<>("[" + index + "]: " + itemValue);
                
                if (item != null && !isPrimitiveOrString(item.getClass())) {
                    addComplexFieldChildren(itemNode, item);
                }
                
                parent.getChildren().add(itemNode);
                index++;
            }
        } 
        // Handle maps
        else if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String keyStr = formatValue(entry.getKey());
                String valueStr = formatValue(entry.getValue());
                
                TreeItem<String> entryNode = new TreeItem<>(keyStr + " => " + valueStr);
                
                if (entry.getValue() != null && !isPrimitiveOrString(entry.getValue().getClass())) {
                    addComplexFieldChildren(entryNode, entry.getValue());
                }
                
                parent.getChildren().add(entryNode);
            }
        }
        // Handle arrays
        else if (value.getClass().isArray()) {
            Object[] array = convertToObjectArray(value);
            for (int i = 0; i < array.length; i++) {
                String itemValue = formatValue(array[i]);
                TreeItem<String> itemNode = new TreeItem<>("[" + i + "]: " + itemValue);
                
                if (array[i] != null && !isPrimitiveOrString(array[i].getClass())) {
                    addComplexFieldChildren(itemNode, array[i]);
                }
                
                parent.getChildren().add(itemNode);
            }
        }
        // For enums, we don't need to go deeper
        else if (value.getClass().isEnum()) {
            return;
        }
        // For other complex objects, use method handles to get their properties
        else {
            Class<?> valueClass = value.getClass();
            Method[] methods = valueClass.getMethods();
            
            // Cache method handles for this object type to improve performance
            Map<Method, MethodHandle> methodHandleCache = new HashMap<>();
            
            for (Method method : methods) {
                String methodName = method.getName();
                Matcher matcher = GETTER_PATTERN.matcher(methodName);
                
                if (matcher.matches() && method.getParameterCount() == 0 && 
                    method.getDeclaringClass() != Object.class &&
                    !methodName.equals("getClass")) {
                    
                    try {
                        // Get or create method handle
                        MethodHandle methodHandle = methodHandleCache.computeIfAbsent(method, m -> {
                            try {
                                return LOOKUP.unreflect(m);
                            } catch (IllegalAccessException e) {
                                return null;
                            }
                        });
                        
                        if (methodHandle == null) continue;
                        
                        String fieldName = Character.toLowerCase(matcher.group(2).charAt(0)) 
                            + matcher.group(2).substring(1);
                        Object fieldValue = methodHandle.invoke(value);
                        String displayValue = formatValue(fieldValue);
                        
                        TreeItem<String> fieldNode = new TreeItem<>(fieldName + ": " + displayValue);
                        
                        if (fieldValue != null && !isPrimitiveOrString(fieldValue.getClass())) {
                            addComplexFieldChildren(fieldNode, fieldValue);
                        }
                        
                        parent.getChildren().add(fieldNode);
                    } catch (Throwable e) {
                        // Skip problematic fields
                    }
                }
            }
        }
    }
    
    /**
     * Formats a value for display in the tree
     */
    private static String formatValue(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "\"" + value + "\"";
        } else if (value instanceof Collection) {
            return "Collection[size=" + ((Collection<?>) value).size() + "]";
        } else if (value instanceof Map) {
            return "Map[size=" + ((Map<?, ?>) value).size() + "]";
        } else if (value.getClass().isArray()) {
            Object[] array = convertToObjectArray(value);
            return "Array[length=" + array.length + "]";
        } else if (value.getClass().isEnum()) {
            return value.toString();
        } else if (isPrimitiveOrString(value.getClass())) {
            return value.toString();
        } else {
            return value.getClass().getSimpleName();
        }
    }
    
    /**
     * Checks if a class is a primitive type or String
     */
    private static boolean isPrimitiveOrString(Class<?> clazz) {
        return clazz.isPrimitive() || 
               clazz == String.class ||
               clazz == Boolean.class ||
               clazz == Byte.class ||
               clazz == Character.class ||
               clazz == Short.class ||
               clazz == Integer.class ||
               clazz == Long.class ||
               clazz == Float.class ||
               clazz == Double.class ||
               clazz.isEnum();
    }
    
    /**
     * Converts any array type to an Object array
     */
    private static Object[] convertToObjectArray(Object array) {
        if (array instanceof Object[]) {
            return (Object[]) array;
        }
        
        int length = java.lang.reflect.Array.getLength(array);
        Object[] result = new Object[length];
        for (int i = 0; i < length; i++) {
            result[i] = java.lang.reflect.Array.get(array, i);
        }
        return result;
    }
    
    /**
     * Represents a field in a packet, with its name, type, and getter method
     */
    @Data
    private static class PacketField {
        private final String name;
        private final Class<?> type;
        private final MethodHandle methodHandle;
    }
}
