package org.eclipse.scanning.device.ui.points;

import java.awt.MouseInfo;
import java.awt.PointerInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.dawnsci.analysis.api.roi.IROI;
import org.eclipse.dawnsci.plotting.api.IPlottingSystem;
import org.eclipse.dawnsci.plotting.api.region.ColorConstants;
import org.eclipse.dawnsci.plotting.api.region.IRegion;
import org.eclipse.dawnsci.plotting.api.region.IRegion.RegionType;
import org.eclipse.dawnsci.plotting.api.region.IRegionSystem;
import org.eclipse.dawnsci.plotting.api.region.RegionUtils;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.richbeans.widgets.internal.GridUtils;
import org.eclipse.richbeans.widgets.menu.MenuAction;
import org.eclipse.scanning.api.points.models.ScanRegion;
import org.eclipse.scanning.device.ui.Activator;
import org.eclipse.scanning.device.ui.util.PlotUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ToolTip;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.part.ViewPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 * A tool for editing scan regions. The view creates/edits regions
 * and then encapsulates then into a ScanRegion object which the axes
 * upon which they act.
 * 
 * It translates the IROI's created in data coordinates to IROI's in
 * axis coordinates and can be used via getAdpter(...) to return a list
 * of these ScanRegions which may be packaged in the CompoundModel which
 * is given to the server.
 * 
 * @author Matthew Gerring
 *
 */
public class ScanRegionView extends ViewPart {
	
	public static final String ID = "org.eclipse.scanning.device.ui.points.scanRegionView";
	
	private static Logger logger = LoggerFactory.getLogger(ScanRegionView.class);

	private static final Collection<RegionType> regionTypes;
	static {
		regionTypes = new ArrayList<RegionType>(6);
		regionTypes.add(RegionType.BOX);
		regionTypes.add(RegionType.POLYGON);
		regionTypes.add(RegionType.RING);
		regionTypes.add(RegionType.SECTOR);
		regionTypes.add(RegionType.ELLIPSE);
	}

	private TableViewer viewer;

	@Override
	public void createPartControl(Composite ancestor) {
		
		Composite control = new Composite(ancestor, SWT.NONE);
		control.setLayout(new GridLayout(1, false));
		GridUtils.removeMargins(control);
		
		this.viewer = new TableViewer(control, SWT.SINGLE | SWT.BORDER | SWT.FULL_SELECTION);
		viewer.setContentProvider(new ScanRegionProvider());
		
		viewer.getTable().setLinesVisible(true);
		viewer.getTable().setHeaderVisible(true);
		viewer.getTable().setLayoutData(new GridData(GridData.FILL_BOTH));
		// resize the row height using a MeasureItem listener
		viewer.getTable().addListener(SWT.MeasureItem, new Listener() {
	        public void handleEvent(Event event) {
	            event.height = 24;
	        }
	    });

	    //added 'event.height=rowHeight' here just to check if it will draw as I want
		viewer.getTable().addListener(SWT.EraseItem, new Listener() {
	        public void handleEvent(Event event) {
	            event.height=24;
	        }
	    });		
		
		createActions();
		
		viewer.setInput(getPlottingSystem());
	}

	private IPlottingSystem<?> getPlottingSystem() {
        // We search for a view which has a PlottingController attached to its plotting system
		// Then we know that this view is designed to respond to mapping.
		IViewPart map = PlotUtil.getRegionView();
		return map.getAdapter(IPlottingSystem.class);
	}

	private void createActions() {

		IToolBarManager toolBarMan = getViewSite().getActionBars().getToolBarManager();
		IMenuManager    menuMan    = getViewSite().getActionBars().getMenuManager();
		MenuManager     rightClick     = new MenuManager();
		List<IContributionManager> mans = Arrays.asList(toolBarMan, menuMan, rightClick);
		
		addGroups("add", mans, createRegionActions());

	}
	
	@Override
	public void setFocus() {
		if (viewer!=null) viewer.getTable().setFocus();
	}
	
	@Override
	public void dispose() {
		if (viewer!=null) viewer.getTable().dispose();
		super.dispose();
	}

	private void addGroups(String id, List<IContributionManager> managers, IAction... actions) {
		for (IContributionManager man : managers) addGroup(id, man, actions);
	}
	private void addGroup(String id, IContributionManager manager, IAction... actions) {
		manager.add(new Separator(id));
		for (IAction action : actions) {
			manager.add(action);
		}
	}

	private IAction createRegionActions() {
		
		final String regionViewName = PlotUtil.getRegionViewName();
		final ToolTip tip = new ToolTip(viewer.getTable().getShell(), SWT.BALLOON);
        
		MenuAction rois = new MenuAction("Add Region");

		for (RegionType regionType : regionTypes) {
			
            IAction action = new Action("Press to click and drag a "+regionType.getName()+" on '"+PlotUtil.getRegionViewName()+"'") {
            	public void run() {
            		createRegion(regionType, regionViewName, tip);
            	}
            };

            action.setImageDescriptor(Activator.getImageDescriptor("icons/ProfileBox.png"));
            rois.add(action);
		}
		
		rois.setSelectedAction(rois.getAction(0));
		return rois;
	}

	private void createRegion(RegionType regionType, final String regionViewName, ToolTip tip) {
		
		IRegionSystem system = PlotUtil.getRegionSystem();
		if (system!=null) {
			try {
				IRegion region = system.createRegion(RegionUtils.getUniqueName("bounding"+regionType.getName(), system), regionType);
				region.setUserObject(new ScanRegion<IROI>(region.getName())); // TODO Axis names!
				region.setRegionColor(ColorConstants.blue);
				region.setAlpha(25);
				region.setLineWidth(1);

				showTip(tip, "Drag a box in the '"+regionViewName+"' to create a bounding box.");

			} catch (Exception e1) {
				logger.error("Cannot create a bounding box!", e1);
				return;
			}
		}
	}

	private void showTip(ToolTip tip, String message) {
    	tip.setMessage(message);
		PointerInfo a = MouseInfo.getPointerInfo();
		java.awt.Point loc = a.getLocation();
		
		tip.setLocation(loc.x, loc.y+20);
        tip.setVisible(true);
	}

}
