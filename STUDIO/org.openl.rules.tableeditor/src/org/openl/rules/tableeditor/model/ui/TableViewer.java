package org.openl.rules.tableeditor.model.ui;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

import org.openl.binding.impl.NodeType;
import org.openl.binding.impl.NodeUsage;
import org.openl.rules.lang.xls.types.CellMetaInfo;
import org.openl.rules.lang.xls.types.meta.MetaInfoReader;
import org.openl.rules.table.CompositeGrid;
import org.openl.rules.table.ICell;
import org.openl.rules.table.ICellComment;
import org.openl.rules.table.IGrid;
import org.openl.rules.table.IGridRegion;
import org.openl.rules.table.IGridTable;
import org.openl.rules.table.ui.ICellStyle;
import org.openl.rules.table.xls.formatters.XlsDataFormatterFactory;
import org.openl.rules.tableeditor.util.Constants;
import org.openl.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class TableViewer {
    private final Logger log = LoggerFactory.getLogger(TableViewer.class);

    private final IGrid grid;
    private final IGridRegion reg;
    private final LinkBuilder linkBuilder;
    private final String mode;
    private final String view;
    private final MetaInfoReader metaInfoReader;
    private final boolean smartNumbers;

    private void setStyle(ICell cell, CellModel cm) {
        ICellStyle style = cell.getStyle();

        if (style == null) {
            return;
        }

        switch (style.getHorizontalAlignment()) {
            case LEFT:
                // Left by default
                break;
            case RIGHT:
                cm.setHalign("right");
                break;
            case CENTER:
                cm.setHalign("center");
                break;
            case JUSTIFY:
                cm.setHalign("justify");
                break;
            default:
                // Align right numeric and date
                if (cell.getNativeType() == IGrid.CELL_TYPE_NUMERIC) {
                    cm.setHalign("right");
                }
                break;
        }

        switch (style.getVerticalAlignment()) {
            case BOTTOM:
                // Left by default
                break;
            case CENTER:
                cm.setValign("center");
                break;
            case TOP:
                cm.setValign("top");
                break;
        }

        if (style.getIndent() > 0) {
            cm.setIndent(style.getIndent());
        }

        short[] rgb = style.getFillForegroundColor();
        cm.setRgbBackground(rgb);

        cm.setFont(cell.getFont());
    }

    public TableViewer(IGrid grid,
            IGridRegion reg,
            LinkBuilder linkBuilder,
            String mode,
            String view,
            MetaInfoReader metaInfoReader,
            boolean smartNumbers) {
        this.grid = grid;
        this.reg = reg;
        this.linkBuilder = linkBuilder;
        this.mode = mode;
        this.view = view;
        this.metaInfoReader = metaInfoReader;
        this.smartNumbers = smartNumbers;
    }

    CellModel buildCell(ICell cell, CellModel cm, CellMetaInfo metaInfo) {
        cm.setColspan(getColSpan(cell));
        cm.setRowspan(getRowSpan(cell));

        if (cm.getRow() == 0) {
            cm.setWidth(getWidth(cell));
        }

        String formattedValue = XlsDataFormatterFactory.getFormattedValue(cell, metaInfo, smartNumbers);
        if (StringUtils.isNotBlank(formattedValue)) {
            String content;
            if (Constants.MODE_EDIT.equals(mode)) {
                // In edit mode there should be no links: it's difficult to start cell editing.
                if (CellMetaInfo.isCellContainsNodeUsages(metaInfo)) {
                    content = createCellWithMetaInfo(formattedValue, metaInfo, false);
                } else {
                    content = escapeHtml4(formattedValue);
                }
            } else if (link(formattedValue)) {
                // has Explanation link
                content = formattedValue;
            } else if (isShowLinks() && (CellMetaInfo
                .isCellContainsNodeUsages(metaInfo) || metaInfo != null && metaInfo.isReturnCell())) {
                // has method call
                content = createCellWithMetaInfo(formattedValue, metaInfo, true);
            } else if (image(formattedValue)) {
                // has image
                content = formattedValue;
            } else if (error(formattedValue)) {
                content = formattedValue;
            } else {
                content = escapeHtml4(formattedValue);
            }
            cm.setContent(content);
            if (cell.getFormula() != null) {
                cm.setFormula(cell.getFormula());
            }
        }

        ICellComment cellComment = cell.getComment();
        cm.setComment(cellComment != null ? cellComment.getText() : null);

        setStyle(cell, cm);
        return cm;
    }

    private boolean image(String formattedValue) {
        return formattedValue.replaceAll("\n", "").matches(".*<i .*>.*</i>.*");
    }

    private boolean error(String formattedValue) {
        return formattedValue.matches(".*<span style=\"color: red;\".*>.*</span>.*");
    }

    private boolean link(String formattedValue) {
        return formattedValue.matches(".*<a href.*</a>.*");
    }

    private String createCellWithMetaInfo(String formattedValue, CellMetaInfo metaInfo, boolean addUri) {
        try {
            int nextSymbolIndex = 0;
            StringBuilder buff = new StringBuilder();
            if (metaInfo.getUsedNodes() != null) {
                for (NodeUsage nodeUsage : metaInfo.getUsedNodes()) {
                    int pstart = nodeUsage.getStart();
                    int pend = nodeUsage.getEnd();
                    String tableUri = nodeUsage.getUri();
                    buff.append(escapeHtml4(formattedValue.substring(nextSymbolIndex, pstart)));
                    // add link to used table with signature in tooltip
                    buff.append("<span class=\"title")
                        .append(" title-")
                        .append(nodeUsage.getNodeType().toString().toLowerCase())
                        .append(" ")
                        .append(Constants.TABLE_EDITOR_META_INFO_CLASS)
                        .append("\">");
                    if (addUri && tableUri != null) {
                        buff.append(
                            linkBuilder.createLinkForTable(tableUri, formattedValue.substring(pstart, pend)));
                    } else {
                        buff.append(escapeHtml4(formattedValue.substring(pstart, pend)));
                    }
                    buff.append("<em>").append(escapeHtml4(nodeUsage.getDescription())).append("</em></span>");
                    nextSymbolIndex = pend;
                }
            }
            buff.append(escapeHtml4(formattedValue.substring(nextSymbolIndex)));

            if (metaInfo.isReturnCell()) {
                buff.append("<span class=\"title title-")
                    .append(NodeType.OTHER.toString().toLowerCase())
                    .append(" ")
                    .append(Constants.TABLE_EDITOR_META_INFO_CLASS)
                    .append("\">");
                buff.append("  &#9733;");
                buff.append("<em>RETURN</em></span>");
            }

            return buff.toString();
        } catch (RuntimeException e) {
            // Fallback to the formula without links
            log.error(e.getMessage(), e);
            return escapeHtml4(formattedValue);
        }
    }

    private boolean isShowLinks() {
        return linkBuilder != null;
    }

    public TableModel buildModel(IGridTable gt, int numRowsToDisplay, List<ICell> modifiedCells, IGridRegion region) {
        int h = IGridRegion.Tool.height(region);
        int w = IGridRegion.Tool.width(region);

        boolean showHeader = true;
        if ("business".equals(view)) {
            showHeader = false;
        }

        TableModel tm = new TableModel(w, h, gt, showHeader);
        tm.setNumRowsToDisplay(numRowsToDisplay);

        if (gt.getGrid() instanceof CompositeGrid) {
            metaInfoReader.prepare(((CompositeGrid) gt.getGrid()).getGridTables()[0].getRegion());
        } else {
            metaInfoReader.prepare(reg);
        }

        if (modifiedCells != null) {
            Set<Integer> modifiedRows = modifiedCells.stream().map(ICell::getRow).collect(Collectors.toSet());
            int lastModifiedRow = modifiedRows.stream().max(Integer::compareTo).orElse(0);
            if (lastModifiedRow >= h) {
                tm = new TableModel(w, lastModifiedRow + 1, gt, showHeader);
            }
            if (numRowsToDisplay > -1) {
                if (numRowsToDisplay < modifiedRows.size()) {
                    modifiedRows = modifiedRows.stream().limit(numRowsToDisplay).collect(Collectors.toSet());
                } else {
                    tm.setNumRowsToDisplay(-1);
                }
            }
            for (int row : modifiedRows) {
                int gridRow = row + region.getTop();
                long count = modifiedCells.stream().filter(c -> c.getRow() == row).count();
                addDisplayedCellToTableModel(tm, gridRow, row, region, count == w ? modifiedCells : null);
            }
        } else {
            for (int gridRow = region.getTop(); gridRow <= region.getBottom(); gridRow++) {
                int row = gridRow - region.getTop();
                addDisplayedCellToTableModel(tm, gridRow, row, region, null);
            }
        }

        setGrid(tm);
        return tm;
    }

    private void addDisplayedCellToTableModel(TableModel tm,
            int gridRow,
            int displayedRowIndex,
            IGridRegion region,
            List<ICell> modifiedCells) {
        for (int column = region.getLeft(); column <= region.getRight(); column++) {
            int c = column - region.getLeft();
            if (tm.hasCell(displayedRowIndex, c)) {
                continue;
            }
            Optional<ICell> changedCell = Optional.empty();
            if (modifiedCells != null) {
                changedCell = modifiedCells.stream()
                    .filter(v -> v.getRow() == displayedRowIndex && v.getColumn() == c)
                    .findFirst();
            }
            ICell cell = changedCell.orElse(grid.getCell(column, gridRow));
            CellMetaInfo metaInfo = metaInfoReader.getMetaInfo(cell.getAbsoluteRow(), cell.getAbsoluteColumn());
            CellModel cm = buildCell(cell, new CellModel(displayedRowIndex, c), metaInfo);
            tm.addCell(cm, displayedRowIndex, c);
            if (cm.getColspan() > 1 || cm.getRowspan() > 1) {
                CellModelDelegator cmd = new CellModelDelegator(cm);
                for (int i = 0; i < cm.getRowspan(); i++) {
                    for (int j = 0; j < cm.getColspan(); j++) {
                        if (i == 0 && j == 0) {
                            continue;
                        }
                        tm.addCell(cmd, displayedRowIndex + i, c + j);
                    }
                }
            }

        }
    }

    BorderStyle getBorderStyle(ICellStyle cs, int side) {

        org.apache.poi.ss.usermodel.BorderStyle xlsStyle;
        short[] rgb;

        org.apache.poi.ss.usermodel.BorderStyle[] bss = cs.getBorderStyle();
        xlsStyle = bss == null ? org.apache.poi.ss.usermodel.BorderStyle.NONE : bss[side];

        short[][] rgbb = cs.getBorderRGB();
        rgb = rgbb == null ? new short[] { 0, 0, 0 } : rgbb[side];

        BorderStyle bs = new BorderStyle();
        bs.setRgb(rgb);
        switch (xlsStyle) {
            case NONE:
                return BorderStyle.NONE;
            case DASH_DOT_DOT:
            case DASH_DOT:
            case DASHED:
                bs.setWidth(1);
                bs.setStyle("dashed");
                break;

            case DOTTED:
                bs.setWidth(1);
                bs.setStyle("dotted");
                break;
            case DOUBLE:
                bs.setWidth(1);
                bs.setStyle("double");
                break;
            case THIN:
                bs.setWidth(1);
                bs.setStyle("solid");
                break;
            case THICK:
                bs.setWidth(2);
                bs.setStyle("solid");
                break;
            case HAIR:
                bs.setWidth(1);
                bs.setStyle("dotted");
                break;
            case MEDIUM:
                bs.setWidth(2);
                bs.setStyle("solid");
                break;
            case MEDIUM_DASH_DOT:
            case MEDIUM_DASH_DOT_DOT:
            case MEDIUM_DASHED:
                bs.setWidth(2);
                bs.setStyle("dashed");
                break;
            default:
                log.warn("Unknown border style: " + xlsStyle);
                bs.setWidth(1);
                bs.setStyle("solid");
        }
        return bs;
    }

    int getColSpan(ICell cell) {
        IGridRegion gr = cell.getRegion();
        if (gr == null) {
            return 1;
        }
        IGridRegion intersect = IGridRegion.Tool.intersect(reg, gr);
        return intersect != null ? IGridRegion.Tool.width(intersect) : 1;
    }

    int getRowSpan(ICell cell) {
        IGridRegion gr = cell.getRegion();
        if (gr == null) {
            return 1;
        }
        IGridRegion intersect = IGridRegion.Tool.intersect(reg, gr);
        return intersect != null ? IGridRegion.Tool.height(intersect) : 1;
    }

    public int getWidth(ICell cell) {
        IGridRegion gr;
        if ((gr = cell.getRegion()) == null) {
            return grid.getColumnWidth(cell.getColumn());
        }
        int w = 0;

        gr = IGridRegion.Tool.intersect(gr, reg);
        if (gr != null) {
            for (int c = gr.getLeft(); c <= gr.getRight(); c++) {
                w += grid.getColumnWidth(c);
            }
        }

        return w;
    }

    short[] rgb(BorderStyle bs1, BorderStyle bs2) {
        if (bs1 == null && bs2 == null) {
            return new short[] { 0, 0, 0 };
        }

        return bs1 == null ? bs2.getRgb()
                           : bs2 == null ? bs1.getRgb() : bs1 == BorderStyle.NONE ? bs2.getRgb() : bs1.getRgb();
    }

    void setGrid(TableModel tm) {
        int width = IGridRegion.Tool.width(reg);

        for (int i = 0; i <= width; i++) {
            setVerticalBorder(i, tm);
        }

        int height = tm.getHeight();

        for (int i = 0; i <= height; i++) {
            setHorizontalBorder(i, tm);
        }

    }

    void setHorizontalBorder(int row, TableModel tm) {
        int width = IGridRegion.Tool.width(reg);
        int left = reg.getLeft();
        int top = reg.getTop();

        for (int i = 0; i < width; i++) {
            ICellStyle ts = row + top - 1 < 0 ? null : grid.getCell(i + left, row + top - 1).getStyle();
            ICellStyle bs = grid.getCell(i + left, row + top).getStyle();

            CellModel cmTop = ts == null ? null : tm.findCellModel(i, row - 1, ICellStyle.BOTTOM);
            CellModel cmBottom = bs == null ? null : tm.findCellModel(i, row, ICellStyle.TOP);

            if (cmTop == null && cmBottom == null) {
                continue;
            }

            BorderStyle tStyle = ts != null ? getBorderStyle(ts, ICellStyle.BOTTOM) : null;
            BorderStyle bStyle = bs != null ? getBorderStyle(bs, ICellStyle.TOP) : null;

            int W = width(tStyle, bStyle);
            String style = style(tStyle, bStyle);
            short[] rgb = rgb(tStyle, bStyle);

            BorderStyle bstyle = new BorderStyle(W, style, rgb);

            switch (W) {
                case 0:
                    break;
                case 1:
                    if (cmTop == null) {
                        cmBottom.setBorderStyle(bstyle, ICellStyle.TOP);

                    } else {
                        cmTop.setBorderStyle(bstyle, ICellStyle.BOTTOM);
                    }
                    break;
                case 2:
                    if (cmTop == null) {
                        cmBottom.setBorderStyle(bstyle, ICellStyle.TOP);
                    } else if (cmBottom == null) {
                        cmTop.setBorderStyle(bstyle, ICellStyle.BOTTOM);
                    } else {
                        bstyle.setWidth(1);
                        cmBottom.setBorderStyle(bstyle, ICellStyle.TOP);
                        cmTop.setBorderStyle(bstyle, ICellStyle.BOTTOM);
                    }

            }
        }

    }

    void setVerticalBorder(int column, TableModel tm) {
        int height = tm.getHeight();
        int left = reg.getLeft();
        int top = reg.getTop();

        for (int i = 0; i < height; i++) {
            ICellStyle ls = column + left - 1 < 0 ? null : grid.getCell(column + left - 1, i + top).getStyle();
            ICellStyle rs = grid.getCell(column + left, i + top).getStyle();

            CellModel cmLeft = ls == null ? null : tm.findCellModel(column - 1, i, ICellStyle.RIGHT);
            CellModel cmRight = rs == null ? null : tm.findCellModel(column, i, ICellStyle.LEFT);

            if (cmLeft == null && cmRight == null) {
                continue;
            }

            BorderStyle lStyle = ls != null ? getBorderStyle(ls, ICellStyle.RIGHT) : null;
            BorderStyle rStyle = rs != null ? getBorderStyle(rs, ICellStyle.LEFT) : null;

            int W = width(lStyle, rStyle);
            String style = style(lStyle, rStyle);
            short[] rgb = rgb(lStyle, rStyle);

            BorderStyle bstyle = new BorderStyle(W, style, rgb);

            switch (W) {
                case 0:
                    break;
                case 1:
                    if (cmLeft == null) {
                        cmRight.setBorderStyle(bstyle, ICellStyle.LEFT);

                    } else {
                        cmLeft.setBorderStyle(bstyle, ICellStyle.RIGHT);
                    }
                    break;
                case 2:
                    if (cmLeft == null) {
                        cmRight.setBorderStyle(bstyle, ICellStyle.LEFT);
                    } else if (cmRight == null) {
                        cmLeft.setBorderStyle(bstyle, ICellStyle.RIGHT);
                    } else {
                        bstyle.setWidth(1);
                        cmRight.setBorderStyle(bstyle, ICellStyle.LEFT);
                        cmLeft.setBorderStyle(bstyle, ICellStyle.RIGHT);
                    }

            }
        }

    }

    String style(BorderStyle bs1, BorderStyle bs2) {
        if (bs1 == null && bs2 == null) {
            return "none";
        }

        return bs1 == null ? bs2.getStyle()
                           : bs2 == null ? bs1.getStyle() : bs1 == BorderStyle.NONE ? bs2.getStyle() : bs1.getStyle();
    }

    int width(BorderStyle bs1, BorderStyle bs2) {
        if (bs1 == null && bs2 == null) {
            return 0;
        }

        return bs1 == null ? bs2.getWidth() : bs2 == null ? bs1.getWidth() : Math.max(bs1.getWidth(), bs2.getWidth());
    }

}
