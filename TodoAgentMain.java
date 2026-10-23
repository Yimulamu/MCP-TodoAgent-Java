import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TodoAgentMain {

    // 在这里替换成你智谱GLM的sk密钥
    private static final String API_KEY = "e1a64530111046149dee3ec0f15c62b0.3RnImCCug3p4Q0Sw";
    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

    // 待办存储
    private static final List<String> todoList = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("===== 待办智能体启动 =====");
        System.out.println("支持：添加待办、查看待办、删除待办");
        System.out.println("输入 exit 退出程序");
        BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            try {
                System.out.print("\n请输入指令：");
                String userInput = consoleReader.readLine();
                if ("exit".equalsIgnoreCase(userInput)) {
                    System.out.println("程序退出");
                    break;
                }
                // 调用大模型
                String llmResult = callLLM(userInput);
                System.out.println("模型返回指令：" + llmResult);
                // 解析指令并且执行工具
                String toolResult = handleToolCommand(llmResult);
                System.out.println("执行结果：" + toolResult);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 解析指令：ADD|xxx  LIST   DEL|数字
     */
    public static String handleToolCommand(String cmd) {
        Pattern addPat = Pattern.compile("ADD\\|(.+)");
        Pattern delPat = Pattern.compile("DEL\\|(\\d+)");
        Matcher addMat = addPat.matcher(cmd.trim());
        Matcher delMat = delPat.matcher(cmd.trim());

        if (addMat.matches()) {
            String content = addMat.group(1);
            todoList.add(content);
            return "✅ 添加成功：" + content;
        } else if (delMat.matches()) {
            int idx = Integer.parseInt(delMat.group(1));
            if (idx >= 0 && idx < todoList.size()) {
                String removeItem = todoList.remove(idx);
                return "✅ 删除成功：" + removeItem;
            } else {
                return "❌ 序号不存在";
            }
        } else if ("LIST".equalsIgnoreCase(cmd.trim())) {
            if (todoList.isEmpty()) {
                return "📋 待办列表为空";
            }
            StringBuilder sb = new StringBuilder("📋 待办列表：\n");
            for (int i = 0; i < todoList.size(); i++) {
                sb.append(i).append(" - ").append(todoList.get(i)).append("\n");
            }
            return sb.toString();
        } else {
            return "⚠️ 无法识别指令";
        }
    }

    public static String callLLM(String userMsg) {
        // 全部写成一行！不要换行，避免JSON非法字符报错
        String systemPrompt = "你是待办事项智能助手。你只能输出下面固定格式指令，不能额外解释、不能闲聊。规则：1.用户添加待办：输出ADD|任务内容；2.用户查看待办：输出LIST；3.用户删除待办：输出DEL|序号（序号从0开始）。禁止输出任何其他文字！";

        String reqBody = "{" +
                "\"model\":\"glm-4-flash\"," +
                "\"messages\":[" +
                "{\"role\":\"system\",\"content\":\"" + systemPrompt.replace("\"", "\\\"") + "\"}," +
                "{\"role\":\"user\",\"content\":\"" + userMsg.replace("\"", "\\\"") + "\"}" +
                "]," +
                "\"temperature\":0.1" +
                "}";

        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            OutputStream os = conn.getOutputStream();
            os.write(reqBody.getBytes(StandardCharsets.UTF_8));
            os.close();

            int code = conn.getResponseCode();
            BufferedReader reader;
            if (code == 200) {
                reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            } else {
                reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            }
            StringBuilder respSb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                respSb.append(line);
            }
            reader.close();
            conn.disconnect();

            String fullJson = respSb.toString();
            System.out.println("【DEBUG 原始JSON】:" + fullJson);

            String searchKey = "\"content\":\"";
            int contentStart = fullJson.indexOf(searchKey);
            if(contentStart == -1){
                return "解析失败，找不到content";
            }
            contentStart += searchKey.length();

            int contentEnd = contentStart;
            while(true){
                contentEnd = fullJson.indexOf("\"", contentEnd);
                if(contentEnd <= 0) break;
                int backSlashCount = 0;
                int checkPos = contentEnd -1;
                while(checkPos >=0 && fullJson.charAt(checkPos) == '\\'){
                    backSlashCount++;
                    checkPos--;
                }
                if(backSlashCount % 2 == 0){
                    break;
                }
                contentEnd += 1;
            }

            if (contentEnd < contentStart) {
                return "解析失败";
            }
            String reply = fullJson.substring(contentStart, contentEnd);
            reply = reply.replace("\\n", "\n").replace("\\\"", "\"");
            return reply.trim();

        } catch (Exception e) {
            e.printStackTrace();
            return "网络请求异常";
        }
    }
}