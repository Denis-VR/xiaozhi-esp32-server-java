package com.xiaozhi.dialogue.llm.tool.function;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.dialogue.llm.memory.Conversation;
import com.xiaozhi.dialogue.llm.memory.ConversationFactory;
import com.xiaozhi.dialogue.llm.tool.ToolCallStringResultConverter;
import com.xiaozhi.dialogue.llm.tool.ToolsGlobalRegistry;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysRole;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysRoleService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 通过语音切换角色函数
 */
@Component
public class ChangeRoleFunction implements ToolsGlobalRegistry.GlobalFunction {
    private static final Logger logger = LoggerFactory.getLogger(ChangeRoleFunction.class);
    @Resource
    private SysRoleService sysRoleService;
    @Resource
    private SysDeviceService sysDeviceService;
    @Resource
    ConversationFactory conversationFactory;

    @Override
    public ToolCallback getFunctionCallTool(ChatSession chatSession) {
        SysDevice sysDevice = chatSession.getSysDevice();
        SysRole queryRole = new SysRole();
        queryRole.setUserId(sysDevice.getUserId());
        PageFilter pageFilter = new PageFilter(1, 5);
        List<SysRole> roleList = sysRoleService.query(queryRole, pageFilter);
        if(!roleList.isEmpty() && roleList.size() > 1) {
            return FunctionToolCallback
                    .builder("func_changeRole", (Map<String, String> params, ToolContext toolContext) -> {
                        String roleName = params.get("roleName");
                        try{
                            // 获取参数
                            Optional<SysRole> changedRole = roleList.stream()
                                    .filter(role -> role.getRoleName().equals(roleName))
                                    .findFirst();


                            if(changedRole.isPresent()){
                                SysRole role = changedRole.get();
                                sysDevice.setRoleId(role.getRoleId());//测试，固定角色
                                sysDeviceService.update(sysDevice);
                                // 切换了角色，需要更换Conversation
                                if(chatSession.getConversation()!=null){
                                    chatSession.getConversation().clear();
                                }
                                Conversation conversation = conversationFactory.initConversation(sysDevice, role, chatSession.getSessionId());
                                chatSession.setConversation(conversation);
                                return "Роль переключена на " + roleName;
                            }else{
                                return "Не удалось переключить роль, соответствующей роли не найдено";
                            }
                        }catch (Exception e){
                            logger.error("Исключение при переключении роли, имя роли: {}", roleName, e);
                            return "Ошибка переключения роли";
                        }
                    })
                    .toolMetadata(ToolMetadata.builder().returnDirect(true).build())
                    .description("当用户想切换角色/助手名字时调用,可选的角色名称列表：" + getRoleList(roleList)
                            + ". 调用前需要先把所有角色名称告知用户,用户告诉你角色名称进行切换.")
                    .inputSchema("""
                        {
                            "type": "object",
                            "properties": {
                                "roleName": {
                                    "type": "string",
                                    "description": "要切换的角色名称"
                                }
                            },
                            "required": ["roleName"]
                        }
                    """)
                    .inputType(Map.class)
                    .toolCallResultConverter(ToolCallStringResultConverter.INSTANCE)
                    .build();
        }
        return null;
    }

    public String getRoleList(List<SysRole> roleList){
        return roleList.stream().map(SysRole::getRoleName).collect(Collectors.joining(", "));
    }
}
