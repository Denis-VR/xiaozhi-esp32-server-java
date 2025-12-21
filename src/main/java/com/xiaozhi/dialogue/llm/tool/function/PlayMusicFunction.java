package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.ChatService;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.dialogue.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.dialogue.service.MusicService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.util.Map;

// @Component  // 音乐服务已停用，暂时禁用此功能
public class PlayMusicFunction implements ToolsGlobalRegistry.GlobalFunction {
    private static final Logger logger = LoggerFactory.getLogger(PlayMusicFunction.class);

    @Resource
    private MusicService musicService;

    ToolCallback toolCallback = FunctionToolCallback
            .builder("func_playMusic", (Map<String, String> params, ToolContext toolContext) -> {
                ChatSession chatSession = (ChatSession)toolContext.getContext().get(ChatService.TOOL_CONTEXT_SESSION_KEY);
                String songName = params.get("songName");
                try{
                    if (songName == null || songName.isEmpty()) {
                        return "Не удалось воспроизвести музыку";
                    }else{
                        musicService.playMusic(chatSession, songName, null);
                        return "Попытка воспроизвести песню «" + songName + "»";
                    }
                }catch (Exception e){
                    logger.error("Исключение при воспроизведении музыки на устройстве, название песни: {}", songName, e);
                    return "音乐播放失败";
                }
            })
            .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
            .description("音乐播放助手,需要用户提供歌曲的名称")
            .inputSchema("""
                        {
                            "type": "object",
                            "properties": {
                                "songName": {
                                    "type": "string",
                                    "description": "要播放的歌曲名称"
                                }
                            },
                            "required": ["songName"]
                        }
                    """)
            .inputType(Map.class)
            .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
            .build();

    @Override
    public ToolCallback getFunctionCallTool(ChatSession chatSession) {
        return toolCallback;
    }
}
