package pro.sketchware.activities.preview;

import android.os.Bundle;

import pro.sketchware.beans.ViewBean;
import pro.sketchware.activities.editor.view.ItemView;
import pro.sketchware.activities.editor.view.ViewPane;
import pro.sketchware.activities.base.BaseAppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import pro.sketchware.core.project.ProjectDataManager;
import pro.sketchware.util.UIHelper;
import pro.sketchware.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityLayoutPreviewBinding;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.tools.ConstraintLayoutParser;
import pro.sketchware.util.SketchwareUtil;
import pro.sketchware.util.UI;

/**
 * Modern Layout Preview Activity with support for:
 * - Traditional Layouts (LinearLayout, RelativeLayout, FrameLayout, etc.)
 * - Modern ConstraintLayout with full constraint support
 * - AndroidX components (CardView, TabLayout, etc.)
 * - Dynamic view rendering with live preview
 *
 * This activity provides instant visual feedback for layout designs without
 * requiring compilation or app installation.
 */
public class LayoutPreviewActivity extends BaseAppCompatActivity {

    private ViewPane pane;
    private String content;
    private String layoutType; // TRADITIONAL, CONSTRAINT, MODERN

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        ActivityLayoutPreviewBinding binding = ActivityLayoutPreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        var toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.layout_preview_title);
        getSupportActionBar().setSubtitle(getIntent().getStringExtra("title"));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        toolbar.setNavigationOnClickListener(v -> {
            if (!UIHelper.isClickThrottled()) {
                getOnBackPressedDispatcher().onBackPressed();
            }
        });
        content = getIntent().getStringExtra("xml");
        var sc_id = getIntent().getStringExtra("sc_id");
        layoutType = getIntent().getStringExtra("layoutType");
        if (layoutType == null) {
            layoutType = "TRADITIONAL"; // Default fallback
        }

        pane = binding.pane;
        pane.initialize(sc_id, true);
        pane.updateRootLayout(sc_id, getIntent().getStringExtra("title"));
        pane.setVerticalScrollBarEnabled(true);
        pane.setResourceManager(ProjectDataManager.getResourceManager(sc_id));
        UI.addSystemWindowInsetToPadding(binding.pane, false, false, false, true);
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (content != null) {
            try {
                // Detect and parse layout type
                ArrayList<ViewBean> parsedViews = parseLayoutByType(content);
                if (!parsedViews.isEmpty()) {
                    loadViews(parsedViews);
                } else {
                    SketchwareUtil.toastError("Failed to parse layout");
                }
            } catch (Exception e) {
                SketchwareUtil.toastError("Error: " + e.toString());
            }
        } else {
            SketchwareUtil.toastError(Helper.getResString(R.string.error_content_null));
        }
    }

    /**
     * Detects the layout type and parses accordingly.
     * Supports:
     * - ConstraintLayout with modern constraints
     * - Traditional layouts (Linear, Relative, Frame)
     * - AndroidX modern components
     *
     * @param xml the XML content to parse
     * @return parsed ViewBean list
     */
    private ArrayList<ViewBean> parseLayoutByType(String xml) throws Exception {
        // Auto-detect layout type from XML
        if (xml.contains("androidx.constraintlayout.widget.ConstraintLayout") ||
            xml.contains("ConstraintLayout") ||
            xml.contains("layout_constraint")) {
            layoutType = "CONSTRAINT";
            return parseConstraintLayout(xml);
        } else if (xml.contains("LinearLayout") || xml.contains("RelativeLayout") ||
                   xml.contains("FrameLayout") || xml.contains("ScrollView")) {
            layoutType = "TRADITIONAL";
            return parseTraditionalLayout(xml);
        } else {
            // Default modern/hybrid approach
            layoutType = "MODERN";
            return parseModernLayout(xml);
        }
    }

    /**
     * Parses ConstraintLayout with full modern constraint support.
     * Handles all constraint-specific attributes for proper positioning and sizing.
     *
     * @param xml the XML content
     * @return parsed ViewBean list with constraint metadata
     */
    private ArrayList<ViewBean> parseConstraintLayout(String xml) throws Exception {
        var parser = new ViewBeanParser(xml);
        ArrayList<ViewBean> views = parser.parse();

        // Extract root attributes for constraint context
        var rootAttrs = parser.getRootAttributes();
        if (rootAttrs != null) {
            applyConstraintMetadata(views, rootAttrs.second);
        }

        // Process each view with ConstraintLayout-specific attributes
        for (ViewBean view : views) {
            // Parse constraint attributes if parent is ConstraintLayout
            if (isConstraintLayoutContext(view.parentType)) {
                Map<String, String> constraintAttrs = new HashMap<>();
                
                // Collect constraint attributes from parentAttributes
                if (view.parentAttributes != null) {
                    for (String key : view.parentAttributes.keySet()) {
                        if (key.contains("layout_constraint")) {
                            constraintAttrs.put(key, view.parentAttributes.get(key));
                        }
                    }
                }

                // Parse constraints using ConstraintLayoutParser
                ConstraintLayoutParser.parseConstraintAttributes(view, constraintAttrs);

                // Apply constraint margins
                if (view.parentAttributes != null) {
                    for (String key : view.parentAttributes.keySet()) {
                        if (key.startsWith("layout_margin")) {
                            ConstraintLayoutParser.applyConstraintMargin(view, key, 
                                    view.parentAttributes.get(key));
                        }
                    }
                }
            }
        }

        return views;
    }

    /**
     * Parses traditional layouts (LinearLayout, RelativeLayout, etc.).
     * Uses standard gravity, weight, and positioning attributes.
     *
     * @param xml the XML content
     * @return parsed ViewBean list
     */
    private ArrayList<ViewBean> parseTraditionalLayout(String xml) throws Exception {
        var parser = new ViewBeanParser(xml);
        return parser.parse();
    }

    /**
     * Parses modern/hybrid layouts with mixed constraint and traditional attributes.
     * Provides best-effort support for complex layouts combining both paradigms.
     *
     * @param xml the XML content
     * @return parsed ViewBean list
     */
    private ArrayList<ViewBean> parseModernLayout(String xml) throws Exception {
        var parser = new ViewBeanParser(xml);
        ArrayList<ViewBean> views = parser.parse();

        // Apply hybrid layout processing
        for (ViewBean view : views) {
            // Try to apply constraint attributes if present
            if (view.parentAttributes != null && !view.parentAttributes.isEmpty()) {
                Map<String, String> hybridAttrs = new HashMap<>(view.parentAttributes);
                ConstraintLayoutParser.parseConstraintAttributes(view, hybridAttrs);
            }
        }

        return views;
    }

    /**
     * Applies constraint metadata from root layout attributes.
     *
     * @param views the parsed views
     * @param rootAttributes the root layout attributes
     */
    private void applyConstraintMetadata(ArrayList<ViewBean> views, Map<String, String> rootAttributes) {
        for (ViewBean view : views) {
            if (view.parentAttributes == null) {
                view.parentAttributes = new HashMap<>();
            }
            // Mark views that have constraint layout as parent
            view.parentAttributes.putAll(rootAttributes);
        }
    }

    /**
     * Determines if a parent type represents a ConstraintLayout.
     * Can be extended to detect custom layout types.
     *
     * @param parentType the parent view type constant
     * @return true if parent is constraint-based
     */
    private boolean isConstraintLayoutContext(int parentType) {
        // Check for constraint layout indicators
        // Can add specific type constants for ConstraintLayout detection
        return layoutType.equals("CONSTRAINT");
    }

    /**
     * Loads parsed views into the preview pane with proper parent-child relationships.
     * Handles view hierarchy rendering and layout calculation.
     *
     * @param views the parsed ViewBean list
     */
    private ItemView loadView(ViewBean view) {
        var itemView = pane.createItemView(view);
        pane.addViewAndUpdateIndex(itemView);
        if (itemView instanceof ItemView sy) {
            sy.setFixed(true);
            return sy;
        }
        return null;
    }

    /**
     * Loads all views maintaining their hierarchy and constraint relationships.
     *
     * @param views the list of ViewBeans to load
     */
    private ItemView loadViews(ArrayList<ViewBean> views) {
        ItemView itemView = null;
        for (ViewBean view : views) {
            if (views.indexOf(view) == 0) {
                view.parent = "root";
                view.parentType = 0;
                view.preParent = null;
                view.preParentType = -1;
                itemView = loadView(view);
            } else {
                loadView(view);
            }
        }
        return itemView;
    }
}
