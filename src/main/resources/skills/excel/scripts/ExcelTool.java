//DEPS com.alibaba:easyexcel:3.3.2
//DEPS org.apache.poi:poi:4.1.2
//DEPS org.apache.poi:poi-ooxml:4.1.2
//DEPS org.slf4j:slf4j-simple:1.7.36

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;

import java.util.*;

public class ExcelTool {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("===== Excel 命令行工具 =====");
            System.out.println("用法：");
            System.out.println("  读取    : jbang ExcelTool.java read <输入路径>");
            System.out.println("  导出    : jbang ExcelTool.java write <输出路径>");
            System.out.println("  读Sheet : jbang ExcelTool.java readSheet <输入路径> <sheet编号>");
            return;
        }

        try {
            String cmd = args[0];
            if ("read".equals(cmd)) {
                List<Map<String, Object>> data = ExcelMapUtil.readLocal(args[1]);
                data.forEach(System.out::println);
            } else if ("readSheet".equals(cmd)) {
                List<Map<String, Object>> data = ExcelMapUtil.readLocal(args[1], Integer.parseInt(args[2]), 1);
                data.forEach(System.out::println);
            } else if ("write".equals(cmd)) {
                LinkedHashMap<Integer, String> head = new LinkedHashMap<>();
                head.put(0, "姓名");
                head.put(1, "年龄");
                head.put(2, "性别");
                head.put(3, "备注");

                List<Map<String, Object>> data = new ArrayList<>();
                Map<String, Object> row = new HashMap<>();
                row.put("姓名", "自动生成");
                row.put("年龄", 25);
                row.put("性别", "男");
                row.put("备注", "JBang 自动依赖");
                data.add(row);

                ExcelMapUtil.writeLocal(args[1], head, data);
                System.out.println("导出成功：" + args[1]);
            }
        } catch (Exception e) {
            System.err.println("执行异常：");
            e.printStackTrace();
        }
    }

    static class ExcelMapUtil {
        private static final String DEFAULT_SHEET_NAME = "Sheet1";

        public static List<Map<String, Object>> readLocal(String filePath) {
            return readLocal(filePath, 0, 1);
        }

        public static List<Map<String, Object>> readLocal(String filePath, int sheetNo, int headRowNumber) {
            MapListener listener = new MapListener();
            EasyExcel.read(filePath, listener).sheet(sheetNo).headRowNumber(headRowNumber).doRead();
            return listener.getDataList();
        }

        public static void writeLocal(String filePath, LinkedHashMap<Integer, String> headMap, List<Map<String, Object>> dataList) {
            EasyExcel.write(filePath)
                    .excelType(ExcelTypeEnum.XLSX)
                    .registerWriteHandler(getDefaultStyle())
                    .head(convertHead(headMap))
                    .sheet(DEFAULT_SHEET_NAME)
                    .doWrite(dataList);
        }

        private static List<List<String>> convertHead(LinkedHashMap<Integer, String> headMap) {
            List<List<String>> list = new ArrayList<>();
            headMap.forEach((k, v) -> list.add(Collections.singletonList(v)));
            return list;
        }

        private static HorizontalCellStyleStrategy getDefaultStyle() {
            WriteCellStyle head = new WriteCellStyle();
            head.setHorizontalAlignment(HorizontalAlignment.CENTER);
            head.setVerticalAlignment(VerticalAlignment.CENTER);
            WriteCellStyle content = new WriteCellStyle();
            content.setHorizontalAlignment(HorizontalAlignment.CENTER);
            content.setVerticalAlignment(VerticalAlignment.CENTER);
            content.setWrapped(true);

            WriteFont hFont = new WriteFont();
            hFont.setFontHeightInPoints((short) 12);
            hFont.setBold(true);
            head.setWriteFont(hFont);
            return new HorizontalCellStyleStrategy(head, content);
        }

        static class MapListener extends AnalysisEventListener<Map<Integer, Object>> {
            private final List<Map<String, Object>> dataList = new ArrayList<>();
            private List<String> heads;

            @Override
            public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
                heads = new ArrayList<>(headMap.values());
            }

            @Override
            public void invoke(Map<Integer, Object> row, AnalysisContext context) {
                Map<String, Object> map = new HashMap<>();
                for (int i = 0; i < heads.size(); i++) {
                    map.put(heads.get(i), row.get(i));
                }
                dataList.add(map);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {}

            public List<Map<String, Object>> getDataList() {
                return dataList;
            }
        }
    }
}