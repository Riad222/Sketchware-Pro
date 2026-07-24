package pro.sketchware.tools;

import android.util.Log;

import pro.sketchware.beans.LayoutBean;
import pro.sketchware.beans.ViewBean;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ConstraintLayoutParser handles parsing of ConstraintLayout-specific attributes
 * into ViewBean properties for modern preview support.
 *
 * Supports:
 * - app:layout_constraint* attributes (Start, End, Top, Bottom, Baseline)
 * - app:layout_constraint*_toStartOf, toEndOf, toTopOf, toBottomOf references
 * - app:layout_constraintDimensionRatio for aspect ratios
 * - Bias attributes (layout_constraintHorizontal_bias, layout_constraintVertical_bias)
 * - Margin attributes in ConstraintLayout context
 */
public class ConstraintLayoutParser {

    private static final String TAG = "ConstraintLayoutParser";

    // Regex patterns for constraint attributes
    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile("layout_constraint([a-zA-Z]+)_to([a-zA-Z]+)Of");
    private static final Pattern DIMENSION_RATIO_PATTERN = Pattern.compile("layout_constraintDimensionRatio");
    private static final Pattern BIAS_PATTERN = Pattern.compile("layout_constraint(Horizontal|Vertical)_bias");

    /**
     * Parses ConstraintLayout-specific attributes from a map and applies them to the ViewBean.
     *
     * @param viewBean the ViewBean to apply attributes to
     * @param attributes map of attribute name → value pairs
     */
    public static void parseConstraintAttributes(ViewBean viewBean, Map<String, String> attributes) {
        if (viewBean == null || attributes == null) {
            return;
        }

        // Store constraints in parentAttributes for later reference
        if (viewBean.parentAttributes == null) {
            viewBean.parentAttributes = new HashMap<>();
        }

        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String attrName = entry.getKey();
            String attrValue = entry.getValue();

            // Skip null values
            if (attrName == null || attrValue == null) continue;

            // Handle dimension ratio
            if (attrName.contains("layout_constraintDimensionRatio")) {
                parseConstraintDimensionRatio(viewBean, attrValue);
            }
            // Handle bias attributes
            else if (attrName.contains("layout_constraintHorizontal_bias")) {
                parseConstraintBias(viewBean, attrValue, true);
            } else if (attrName.contains("layout_constraintVertical_bias")) {
                parseConstraintBias(viewBean, attrValue, false);
            }
            // Handle constraint references (toStartOf, toEndOf, etc.)
            else if (isConstraintAttribute(attrName)) {
                storeConstraintReference(viewBean, attrName, attrValue);
            }
        }

        Log.d(TAG, "Parsed constraints for view: " + viewBean.id);
    }

    /**
     * Checks if an attribute is a ConstraintLayout constraint attribute.
     *
     * @param attrName the attribute name to check
     * @return true if it's a constraint attribute
     */
    private static boolean isConstraintAttribute(String attrName) {
        return attrName.startsWith("layout_constraint") &&
                (attrName.contains("_toStartOf") || attrName.contains("_toEndOf") ||
                 attrName.contains("_toTopOf") || attrName.contains("_toBottomOf") ||
                 attrName.contains("_toBaselineOf"));
    }

    /**
     * Stores a constraint reference in parentAttributes for rendering.
     *
     * @param viewBean the ViewBean to update
     * @param attrName the attribute name (e.g., "layout_constraintStart_toStartOf")
     * @param attrValue the target view ID or parent reference
     */
    private static void storeConstraintReference(ViewBean viewBean, String attrName, String attrValue) {
        // Extract the target edge and direction
        // Example: "layout_constraintStart_toStartOf" → "Start" and "toStartOf"
        Matcher matcher = CONSTRAINT_PATTERN.matcher(attrName);

        if (matcher.find()) {
            String fromEdge = matcher.group(1);  // "Start", "End", "Top", "Bottom"
            String toEdge = matcher.group(2);    // "StartOf", "EndOf", "TopOf", "BottomOf"

            // Store in parentAttributes for reference
            viewBean.parentAttributes.put(attrName, attrValue);

            Log.d(TAG, "Stored constraint: " + fromEdge + " to " + toEdge + " (target: " + attrValue + ")");
        }
    }

    /**
     * Parses layout_constraintDimensionRatio and applies it to the ViewBean.
     * Examples: "16:9", "1.5", "H,16:9"
     *
     * @param viewBean the ViewBean to update
     * @param ratioValue the ratio string
     */
    private static void parseConstraintDimensionRatio(ViewBean viewBean, String ratioValue) {
        if (ratioValue == null || ratioValue.isEmpty()) {
            return;
        }

        // Handle "H,16:9" or "W,16:9" format
        if (ratioValue.contains(",")) {
            ratioValue = ratioValue.substring(ratioValue.indexOf(",") + 1).trim();
        }

        try {
            // Handle "width:height" format
            if (ratioValue.contains(":")) {
                String[] parts = ratioValue.split(":");
                float width = Float.parseFloat(parts[0]);
                float height = Float.parseFloat(parts[1]);
                float ratio = width / height;
                viewBean.scaleX = ratio;
                Log.d(TAG, "Applied dimension ratio " + ratio + " to view: " + viewBean.id);
            } else {
                // Direct float ratio
                float ratio = Float.parseFloat(ratioValue);
                viewBean.scaleX = ratio;
                Log.d(TAG, "Applied dimension ratio " + ratio + " to view: " + viewBean.id);
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse dimension ratio: " + ratioValue, e);
        }
    }

    /**
     * Parses layout_constraintHorizontal_bias or layout_constraintVertical_bias.
     * Values range from 0.0 (start/top) to 1.0 (end/bottom).
     *
     * @param viewBean the ViewBean to update
     * @param biasValue the bias value as a string
     * @param isHorizontal true for horizontal bias, false for vertical
     */
    private static void parseConstraintBias(ViewBean viewBean, String biasValue, boolean isHorizontal) {
        if (biasValue == null || biasValue.isEmpty()) {
            return;
        }

        try {
            float bias = Float.parseFloat(biasValue);
            // Clamp to 0.0 - 1.0 range
            bias = Math.max(0.0f, Math.min(1.0f, bias));

            if (isHorizontal) {
                viewBean.parentAttributes.put("layout_constraintHorizontal_bias", String.valueOf(bias));
                Log.d(TAG, "Applied horizontal bias " + bias + " to view: " + viewBean.id);
            } else {
                viewBean.parentAttributes.put("layout_constraintVertical_bias", String.valueOf(bias));
                Log.d(TAG, "Applied vertical bias " + bias + " to view: " + viewBean.id);
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse bias value: " + biasValue, e);
        }
    }

    /**
     * Applies ConstraintLayout margins. In ConstraintLayout, margins are set
     * like layout_marginStart, layout_marginEnd, layout_marginTop, layout_marginBottom.
     *
     * @param viewBean the ViewBean to update
     * @param attrName the attribute name
     * @param attrValue the margin value
     */
    public static void applyConstraintMargin(ViewBean viewBean, String attrName, String attrValue) {
        if (viewBean.layout == null) {
            viewBean.layout = new LayoutBean();
        }

        int marginValue = parseDpValue(attrValue);

        switch (attrName) {
            case "layout_marginStart":
                viewBean.layout.marginLeft = marginValue;
                break;
            case "layout_marginEnd":
                viewBean.layout.marginRight = marginValue;
                break;
            case "layout_marginTop":
                viewBean.layout.marginTop = marginValue;
                break;
            case "layout_marginBottom":
                viewBean.layout.marginBottom = marginValue;
                break;
        }
    }

    /**
     * Checks if a parent is ConstraintLayout.
     *
     * @param parentType the parent view type constant
     * @return true if parent is a ConstraintLayout type
     */
    public static boolean isConstraintLayoutParent(int parentType) {
        // You can add a specific type constant for ConstraintLayout
        // For now, we check by type name or use a heuristic
        return parentType == ViewBean.VIEW_TYPE_LAYOUT_LINEAR; // Placeholder: actual type detection needed
    }

    /**
     * Parses a dimension value like "16dp" to an integer pixel value.
     *
     * @param dimensionValue the dimension string
     * @return the parsed value, or 0 if parsing fails
     */
    private static int parseDpValue(String dimensionValue) {
        if (dimensionValue == null || dimensionValue.isEmpty()) {
            return 0;
        }

        try {
            // Strip unit suffixes
            String numericPortion = dimensionValue.replaceAll("(dp|dip|sp|px|pt|in|mm)$", "");
            return (int) Float.parseFloat(numericPortion);
        } catch (NumberFormatException e) {
            Log.w(TAG, "Failed to parse dimension value: " + dimensionValue);
            return 0;
        }
    }

    /**
     * Gets a constraint reference target from the stored attributes.
     *
     * @param viewBean the ViewBean to query
     * @param constraintAttr the constraint attribute name
     * @return the target view ID, or null if not found
     */
    public static String getConstraintTarget(ViewBean viewBean, String constraintAttr) {
        if (viewBean.parentAttributes == null) {
            return null;
        }
        return viewBean.parentAttributes.get(constraintAttr);
    }

    /**
     * Gets the horizontal bias of a view.
     *
     * @param viewBean the ViewBean to query
     * @return the bias value (0.0 to 1.0), or 0.5 (center) if not set
     */
    public static float getHorizontalBias(ViewBean viewBean) {
        if (viewBean.parentAttributes == null) {
            return 0.5f;
        }

        String biasStr = viewBean.parentAttributes.get("layout_constraintHorizontal_bias");
        if (biasStr != null) {
            try {
                return Float.parseFloat(biasStr);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.5f;
    }

    /**
     * Gets the vertical bias of a view.
     *
     * @param viewBean the ViewBean to query
     * @return the bias value (0.0 to 1.0), or 0.5 (center) if not set
     */
    public static float getVerticalBias(ViewBean viewBean) {
        if (viewBean.parentAttributes == null) {
            return 0.5f;
        }

        String biasStr = viewBean.parentAttributes.get("layout_constraintVertical_bias");
        if (biasStr != null) {
            try {
                return Float.parseFloat(biasStr);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.5f;
    }
}
