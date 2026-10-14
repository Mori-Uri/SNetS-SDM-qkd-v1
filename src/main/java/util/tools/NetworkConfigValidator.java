package util.tools;

import java.util.*;

import simulationControl.parsers.NetworkConfig;

/**
 * Structural validator for NetworkConfig.
 */
public class NetworkConfigValidator {

	private static final double EPS = 1e-9;

    /**
     * Creates a new instance of NetworkConfigValidator.
     */
    private NetworkConfigValidator() {
    	//
    }

    /**
     * Validates the value.
     * @param config the config.
     * @return the result of the operation.
     */
    public static ValidationResult validate(NetworkConfig config) {
        ValidationResult result = new ValidationResult();

        if (config == null) {
            result.addError("NetworkConfig  null.");
            return result;
        }

        List<NetworkConfig.NodeConfig> nodes = config.getNodes() != null ? config.getNodes() : Collections.emptyList();

        List<NetworkConfig.LinkConfig> links = config.getLinks() != null ? config.getLinks() : Collections.emptyList();

        List<NetworkConfig.CoreConfig> cores = config.getCores() != null ? config.getCores() : Collections.emptyList();

        NodeValidationContext nodeCtx = validateNodes(nodes, result);
        LinkValidationContext linkCtx = validateLinks(nodeCtx, links, result);

        validateGraphConnectivity(nodeCtx, linkCtx, result);
        validateCores(cores, result);

        return result;
    }

    /**
     * Validates the or throw.
     * @param config the config.
     */
    public static void validateOrThrow(NetworkConfig config) {
        ValidationResult result = validate(config);
        if (!result.isValid()) {
            throw new IllegalStateException(result.toPrettyString());
        }
    }

    /**
     * Validates the nodes.
     * @param nodes the nodes.
     * @param result the result.
     * @return the result of the operation.
     */
    private static NodeValidationContext validateNodes(List<NetworkConfig.NodeConfig> nodes, ValidationResult result) {
        NodeValidationContext ctx = new NodeValidationContext();

        if (nodes.isEmpty()) {
            result.addError("The network has no registered nodes.");
            return ctx;
        }

        for (int i = 0; i < nodes.size(); i++) {
            NetworkConfig.NodeConfig node = nodes.get(i);

            if (node == null) {
                result.addError("There is a null NodeConfig at position " + i + ".");
                continue;
            }

            String name = safeTrim(node.getName());

            if (name.isEmpty()) {
                result.addError("There is a node with an empty name at position " + i + ".");
                continue;
            }

            if (!ctx.validNodes.add(name)) {
                result.addError("Duplicate node name: '" + name + "'.");
            }
        }

        return ctx;
    }

    /**
     * Validates the links.
     * @param nodeCtx the nodeCtx.
     * @param links the links.
     * @param result the result.
     * @return the result of the operation.
     */
    private static LinkValidationContext validateLinks(NodeValidationContext nodeCtx, List<NetworkConfig.LinkConfig> links, ValidationResult result) {
        LinkValidationContext ctx = new LinkValidationContext();

        if (links.isEmpty()) {
            result.addError("The network has no registered links.");
            return ctx;
        }

        for (String nodeName : nodeCtx.validNodes) {
            ctx.incomingCount.put(nodeName, 0);
            ctx.outgoingCount.put(nodeName, 0);
            ctx.adjacency.put(nodeName, new HashSet<>());
        }

        for (int i = 0; i < links.size(); i++) {
            NetworkConfig.LinkConfig link = links.get(i);

            if (link == null) {
                result.addError("There is a null LinkConfig at position " + i + ".");
                continue;
            }

            String source = safeTrim(link.getSource());
            String destination = safeTrim(link.getDestination());

            if (source.isEmpty()) {
                result.addError("Link at position " + i + " has an empty source.");
                continue;
            }

            if (destination.isEmpty()) {
                result.addError("Link at position " + i + " has an empty destination.");
                continue;
            }

            if (!nodeCtx.validNodes.contains(source)) {
                result.addError("Link " + source + " -> " + destination + " references a non-existing source.");
            }

            if (!nodeCtx.validNodes.contains(destination)) {
                result.addError("Link " + source + " -> " + destination + " references a non-existing destination.");
            }

            if (source.equals(destination)) {
                result.addError("Link invlido do tipo auto-loop: '" + source + " -> " + destination + "'.");
            }

            if (link.getSlots() <= 0) {
                result.addError("Link " + source + " -> " + destination + " has slots <= 0.");
            }

            if (link.getSpectrum() <= 0.0) {
                result.addError("Link " + source + " -> " + destination + " has spectrum <= 0.");
            }

            if (link.getSize() < 0.0) {
                result.addError("Link " + source + " -> " + destination + " has a negative size.");
            }

            boolean sourceExists = nodeCtx.validNodes.contains(source);
            boolean destinationExists = nodeCtx.validNodes.contains(destination);

            if (sourceExists) {
                ctx.outgoingCount.put(source, ctx.outgoingCount.get(source) + 1);
            }

            if (destinationExists) {
                ctx.incomingCount.put(destination, ctx.incomingCount.get(destination) + 1);
            }

            if (sourceExists && destinationExists && !source.equals(destination)) {
                ctx.adjacency.get(source).add(destination);
                ctx.adjacency.get(destination).add(source);
            }

            DirectedPair pair = new DirectedPair(source, destination);
            LinkSignature sig = new LinkSignature(link.getSize(), link.getSlots(), link.getSpectrum());

            ctx.pairToSignatures
                    .computeIfAbsent(pair, k -> new HashMap<>())
                    .merge(sig, 1, Integer::sum);
        }

        for (String nodeName : nodeCtx.validNodes) {
            if (ctx.incomingCount.getOrDefault(nodeName, 0) == 0) {
                result.addError("The node '" + nodeName + "' has no incoming link.");
            }

            if (ctx.outgoingCount.getOrDefault(nodeName, 0) == 0) {
                result.addError("The node '" + nodeName + "' has no outgoing link.");
            }
        }

        detectDuplicateLinks(ctx.pairToSignatures, result);
        detectMissingOrInvalidReverseLinks(ctx.pairToSignatures, result);

        return ctx;
    }

    /**
     * Executes the detect duplicate links operation.
     * @param pairToSignatures the pairToSignatures.
     * @param result the result.
     */
    private static void detectDuplicateLinks(
            Map<DirectedPair, Map<LinkSignature, Integer>> pairToSignatures,
            ValidationResult result
    ) {
        for (Map.Entry<DirectedPair, Map<LinkSignature, Integer>> pairEntry : pairToSignatures.entrySet()) {
            DirectedPair pair = pairEntry.getKey();

            for (Map.Entry<LinkSignature, Integer> sigEntry : pairEntry.getValue().entrySet()) {
                int count = sigEntry.getValue();
                if (count > 1) {
                    LinkSignature sig = sigEntry.getKey();
                    result.addError(
                            "Duplicate link detected for '" + pair.source + " -> " + pair.destination +
                            "' with size=" + sig.size +
                            ", slots=" + sig.slots +
                            ", spectrum=" + sig.spectrum +
                            ". Occurrences: " + count + "."
                    );
                }
            }
        }
    }

    /**
     * Executes the detect missing or invalid reverse links operation.
     * @param pairToSignatures the pairToSignatures.
     * @param result the result.
     */
    private static void detectMissingOrInvalidReverseLinks(
            Map<DirectedPair, Map<LinkSignature, Integer>> pairToSignatures,
            ValidationResult result
    ) {
        Set<UndirectedPair> mismatchedReported = new HashSet<>();

        for (Map.Entry<DirectedPair, Map<LinkSignature, Integer>> entry : pairToSignatures.entrySet()) {
            DirectedPair direct = entry.getKey();
            DirectedPair reverse = new DirectedPair(direct.destination, direct.source);

            Map<LinkSignature, Integer> directMap = entry.getValue();
            Map<LinkSignature, Integer> reverseMap = pairToSignatures.getOrDefault(reverse, Collections.emptyMap());

            for (Map.Entry<LinkSignature, Integer> sigEntry : directMap.entrySet()) {
                LinkSignature sig = sigEntry.getKey();
                int directCount = sigEntry.getValue();
                int reverseCount = reverseMap.getOrDefault(sig, 0);

                if (reverseCount < directCount) {
                    int missing = directCount - reverseCount;
                    for (int i = 0; i < missing; i++) {
                        result.addError(
                                "Missing reverse link for '" + direct.source + " -> " + direct.destination +
                                "' with size=" + sig.size +
                                ", slots=" + sig.slots +
                                ", spectrum=" + sig.spectrum +
                                ". Expected: '" + direct.destination + " -> " + direct.source + "'."
                        );
                    }
                }
            }

            if (!reverseMap.isEmpty()) {
                boolean hasAnyExactMatch = false;
                for (LinkSignature sig : directMap.keySet()) {
                    if (reverseMap.containsKey(sig)) {
                        hasAnyExactMatch = true;
                        break;
                    }
                }

                if (!hasAnyExactMatch) {
                    UndirectedPair up = new UndirectedPair(direct.source, direct.destination);
                    if (mismatchedReported.add(up)) {
                        result.addError(
                                "There is a bidirectional link between '" + direct.source + "' and '" + direct.destination +
                                "', but there is no exact size/slots/spectrum match between directions."
                        );
                    }
                }
            }
        }
    }

    /**
     * Validates the graph connectivity.
     * @param nodeCtx the nodeCtx.
     * @param linkCtx the linkCtx.
     * @param result the result.
     */
    private static void validateGraphConnectivity(
            NodeValidationContext nodeCtx,
            LinkValidationContext linkCtx,
            ValidationResult result
    ) {
        if (nodeCtx.validNodes.isEmpty()) {
            return;
        }

        String start = nodeCtx.validNodes.iterator().next();
        Set<String> visited = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();

        stack.push(start);
        visited.add(start);

        while (!stack.isEmpty()) {
            String current = stack.pop();

            for (String neighbor : linkCtx.adjacency.getOrDefault(current, Collections.emptySet())) {
                if (visited.add(neighbor)) {
                    stack.push(neighbor);
                }
            }
        }

        if (visited.size() != nodeCtx.validNodes.size()) {
            Set<String> missing = new TreeSet<>(nodeCtx.validNodes);
            missing.removeAll(visited);

            result.addError(
                    "The network is not fully connected. Unreachable nodes starting from '" +
                    start + "': " + missing
            );
        }
    }

    /**
     * Validates the cores.
     * @param cores the cores.
     * @param result the result.
     */
    private static void validateCores(List<NetworkConfig.CoreConfig> cores, ValidationResult result) {
        if (cores.isEmpty()) {
            result.addWarning("The configuration has no registered cores.");
            return;
        }

        Map<Integer, NetworkConfig.CoreConfig> coreById = new HashMap<>();

        for (int i = 0; i < cores.size(); i++) {
            NetworkConfig.CoreConfig core = cores.get(i);

            if (core == null) {
                result.addError("There is a null CoreConfig at position " + i + ".");
                continue;
            }

            int id = core.getId();

            if (coreById.containsKey(id)) {
                result.addError("Duplicate core with id=" + id + ".");
            } else {
                coreById.put(id, core);
            }
        }

        for (NetworkConfig.CoreConfig core : cores) {
            if (core == null) {
                continue;
            }

            int coreId = core.getId();
            List<Integer> adjacents =
                    core.getAdjacentCores() != null ? core.getAdjacentCores() : Collections.emptyList();

            Set<Integer> seenAdj = new HashSet<>();

            for (Integer adjId : adjacents) {
                if (adjId == null) {
                    result.addError("Core " + coreId + " has null adjacency.");
                    continue;
                }

                if (adjId == coreId) {
                    result.addError("Core " + coreId + " has self-adjacency.");
                }

                if (!seenAdj.add(adjId)) {
                    result.addError(
                            "Core " + coreId + " has repeated adjacency to core " + adjId + "."
                    );
                    continue;
                }

                NetworkConfig.CoreConfig adjCore = coreById.get(adjId);
                if (adjCore == null) {
                    result.addError("Core " + coreId + " points to a non-existing adjacent core: " + adjId + ".");
                    continue;
                }

                List<Integer> reverseAdj =
                        adjCore.getAdjacentCores() != null ? adjCore.getAdjacentCores() : Collections.emptyList();

                if (!reverseAdj.contains(coreId)) {
                    result.addError(
                            "Non-reciprocal core adjacency: core " + coreId +
                            " points to " + adjId +
                            ", but core " + adjId +
                            " does not point back to " + coreId + "."
                    );
                }
            }
        }
    }

    /**
     * Returns the safe trim.
     * @param s the s.
     * @return the result of the operation.
     */
    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static final class NodeValidationContext {
        private final Set<String> validNodes = new LinkedHashSet<>();
    }

    private static final class LinkValidationContext {
        private final Map<String, Integer> incomingCount = new HashMap<>();
        private final Map<String, Integer> outgoingCount = new HashMap<>();
        private final Map<String, Set<String>> adjacency = new HashMap<>();
        private final Map<DirectedPair, Map<LinkSignature, Integer>> pairToSignatures = new HashMap<>();
    }

    private static final class DirectedPair {
        private final String source;
        private final String destination;

        private DirectedPair(String source, String destination) {
            this.source = source;
            this.destination = destination;
        }

        /**
         * Checks whether this object is equal to another object.
         * @param obj the obj.
         * @return the result.
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof DirectedPair)) return false;
            DirectedPair other = (DirectedPair) obj;
            return Objects.equals(source, other.source) && Objects.equals(destination, other.destination);
        }

        /**
         * Returns the hash code for this object.
         * @return the result.
         */
        @Override
        public int hashCode() {
            return Objects.hash(source, destination);
        }
    }

    private static final class UndirectedPair {
        private final String a;
        private final String b;

        private UndirectedPair(String x, String y) {
            if (x.compareTo(y) <= 0) {
                this.a = x;
                this.b = y;
            } else {
                this.a = y;
                this.b = x;
            }
        }

        /**
         * Checks whether this object is equal to another object.
         * @param obj the obj.
         * @return the result.
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof UndirectedPair)) return false;
            UndirectedPair other = (UndirectedPair) obj;
            return Objects.equals(a, other.a) && Objects.equals(b, other.b);
        }

        /**
         * Returns the hash code for this object.
         * @return the result.
         */
        @Override
        public int hashCode() {
            return Objects.hash(a, b);
        }
    }

    private static final class LinkSignature {
        private final double size;
        private final int slots;
        private final double spectrum;

        private LinkSignature(double size, int slots, double spectrum) {
            this.size = normalize(size);
            this.slots = slots;
            this.spectrum = normalize(spectrum);
        }

        /**
         * Returns the normalize.
         * @param value the value.
         * @return the result of the operation.
         */
        private static double normalize(double value) {
            if (Math.abs(value) < EPS) {
                return 0.0;
            }
            return value;
        }

        /**
         * Checks whether this object is equal to another object.
         * @param obj the obj.
         * @return the result.
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof LinkSignature)) return false;
            LinkSignature other = (LinkSignature) obj;
            return slots == other.slots
                    && Math.abs(size - other.size) < EPS
                    && Math.abs(spectrum - other.spectrum) < EPS;
        }

        /**
         * Returns the hash code for this object.
         * @return the result.
         */
        @Override
        public int hashCode() {
            long roundedSize = Math.round(size / EPS);
            long roundedSpectrum = Math.round(spectrum / EPS);
            return Objects.hash(roundedSize, slots, roundedSpectrum);
        }
    }

    public static final class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        /**
         * Checks whether valid.
         * @return true if the condition is met; false otherwise.
         */
        public boolean isValid() {
            return errors.isEmpty();
        }

        /**
         * Returns the errors.
         * @return the errors.
         */
        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        /**
         * Returns the warnings.
         * @return the warnings.
         */
        public List<String> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }

        /**
         * Adds the error.
         * @param message the message.
         */
        public void addError(String message) {
            errors.add(message);
        }

        /**
         * Adds the warning.
         * @param message the message.
         */
        public void addWarning(String message) {
            warnings.add(message);
        }

        /**
         * Executes the throw if invalid operation.
         */
        public void throwIfInvalid() {
            if (!isValid()) {
                throw new IllegalStateException(toPrettyString());
            }
        }

        /**
         * Returns the to pretty string.
         * @return the result of the operation.
         */
        public String toPrettyString() {
            StringBuilder sb = new StringBuilder();

            sb.append("Validation Network {\n");
            sb.append("  valid = ").append(isValid()).append(",\n");
            sb.append("  errors = ").append(errors.size()).append(",\n");
            sb.append("  warnings = ").append(warnings.size()).append("\n");

            if (!errors.isEmpty()) {
                sb.append("  Error list:\n");
                for (String error : errors) {
                    sb.append("    - ").append(error).append('\n');
                }
            }

            if (!warnings.isEmpty()) {
                sb.append("  Warning list:\n");
                for (String warning : warnings) {
                    sb.append("    - ").append(warning).append('\n');
                }
            }

            sb.append("}");
            return sb.toString();
        }

        /**
         * Returns the string representation of this object.
         * @return the result.
         */
        @Override
        public String toString() {
            return toPrettyString();
        }
    }
}
