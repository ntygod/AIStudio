Temperature 设置
temperature 参数默认为 1.0。

我们建议您根据如下表格，按使用场景设置 temperature。
场景	温度
代码生成/数学解题   	0.0
数据抽取/分析	1.0
通用对话	1.3
翻译	1.3
创意类写作/诗歌创作	1.5



Tool Calls
Tool Calls 让模型能够调用外部工具，来增强自身能力。

非思考模式
样例代码
这里以获取用户当前位置的天气信息为例，展示了使用 Tool Calls 的完整 Python 代码。

Tool Calls 的具体 API 格式请参考对话补全文档。

from openai import OpenAI

def send_messages(messages):
    response = client.chat.completions.create(
        model="deepseek-chat",
        messages=messages,
        tools=tools
    )
    return response.choices[0].message

client = OpenAI(
    api_key="<your api key>",
    base_url="https://api.deepseek.com",
)

tools = [
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "Get weather of a location, the user should supply a location first.",
            "parameters": {
                "type": "object",
                "properties": {
                    "location": {
                        "type": "string",
                        "description": "The city and state, e.g. San Francisco, CA",
                    }
                },
                "required": ["location"]
            },
        }
    },
]

messages = [{"role": "user", "content": "How's the weather in Hangzhou, Zhejiang?"}]
message = send_messages(messages)
print(f"User>\t {messages[0]['content']}")

tool = message.tool_calls[0]
messages.append(message)

messages.append({"role": "tool", "tool_call_id": tool.id, "content": "24℃"})
message = send_messages(messages)
print(f"Model>\t {message.content}")

这个例子的执行流程如下：

用户：询问现在的天气
模型：返回 function get_weather({location: 'Hangzhou'})
用户：调用 function get_weather({location: 'Hangzhou'})，并传给模型。
模型：返回自然语言，"The current temperature in Hangzhou is 24°C."
注：上述代码中 get_weather 函数功能需由用户提供，模型本身不执行具体函数。

思考模式
从 DeepSeek-V3.2 开始，API 支持了思考模式下的工具调用能力，详见思考模式。

strict 模式（Beta）
在 strict 模式下，模型在输出 Function 调用时会严格遵循 Function 的 JSON Schema 的格式要求，以确保模型输出的 Function 符合用户的定义。在思考与非思考模式下的工具调用，均可使用 strict 模式。

要使用 strict 模式，需要：

用户需要设置 base_url="https://api.deepseek.com/beta" 来开启 Beta 功能
在传入的 tools 列表中，所有 function 均需设置 strict 属性为 true
服务端会对用户传入的 Function 的 JSON Schema 进行校验，如不符合规范，或遇到服务端不支持的 JSON Schema 类型，将返回错误信息
以下是 strict 模式下 tool 的定义样例：

{
    "type": "function",
    "function": {
        "name": "get_weather",
        "strict": true,
        "description": "Get weather of a location, the user should supply a location first.",
        "parameters": {
            "type": "object",
            "properties": {
                "location": {
                    "type": "string",
                    "description": "The city and state, e.g. San Francisco, CA",
                }
            },
            "required": ["location"],
            "additionalProperties": false
        }
    }
}

strict 模式支持的 JSON Schema 类型
object
string
number
integer
boolean
array
enum
anyOf
object 类型
object 定义一个包含键值对的深层结构，其中 properties 定义了对象中每个键（属性）的 schema。每个 object 的所有属性均需设置为 required，且 object 中 additionalProperties 属性必须为 false。

示例：

{
    "type": "object",
    "properties": {
        "name": { "type": "string" },
        "age": { "type": "integer" }
    },
    "required": ["name", "age"],
    "additionalProperties": false
}

string 类型
支持的参数：
pattern：使用正则表达式来约束字符串的格式
format：使用预定义的常见格式进行校验，目前支持：
email：电子邮件地址
hostname：主机名
ipv4：IPv4 地址
ipv6：IPv6 地址
uuid：uuid
不支持的参数
minLength
maxLength
示例：

{
    "type": "object",
    "properties": {
        "user_email": {
            "type": "string",
            "description": "The user's email address",
            "format": "email" 
        },
        "zip_code": {
            "type": "string",
            "description": "Six digit postal code",
            "pattern": "^\\d{6}$"
        }
    }
}

number/integer 类型
支持的参数
const：固定数字为常数
default：数字的默认值
minimum：最小值
maximum：最大值
exclusiveMinimum：不小于
exclusiveMaximum：不大于
multipleOf：数字输出为这个值的倍数
示例：

{
    "type": "object",
    "properties": {
        "score": {
            "type": "integer",
            "description": "A number from 1-5, which represents your rating, the higher, the better",
            "minimum": 1,
            "maximum": 5
        }
    },
    "required": ["score"],
    "additionalProperties": false
}

array 类型
不支持的参数
minItems
maxItems
示例：

{
    "type": "object",
    "properties": {
        "keywords": {
            "type": "array",
            "description": "Five keywords of the article, sorted by importance",
            "items": {
                "type": "string",
                "description": "A concise and accurate keyword or phrase."
            }
        }
    },
    "required": ["keywords"],
    "additionalProperties": false
}

enum
enum 可以确保输出是预期的几个选项之一，例如在订单状态的场景下，只能是有限几个状态之一。

样例：

{
    "type": "object",
    "properties": {
        "order_status": {
            "type": "string",
            "description": "Ordering status",
            "enum": ["pending", "processing", "shipped", "cancelled"]
        }
    }
}

anyOf
匹配所提供的多个 schema 中的任意一个，可以处理可能具有多种有效格式的字段，例如用户的账户可能是邮箱或者手机号中的一个：

{
    "type": "object",
    "properties": {
    "account": {
        "anyOf": [
            { "type": "string", "format": "email", "description": "可以是电子邮件地址" },
            { "type": "string", "pattern": "^\\d{11}$", "description": "或11位手机号码" }
        ]
    }
  }
}

$ref 和 $def
可以使用 $def 定义模块，再用 $ref 引用以减少模式的重复和模块化，此外还可以单独使用 $ref 定义递归结构。

{
    "type": "object",
    "properties": {
        "report_date": {
            "type": "string",
            "description": "The date when the report was published"
        },
        "authors": {
            "type": "array",
            "description": "The authors of the report",
            "items": {
                "$ref": "#/$def/author"
            }
        }
    },
    "required": ["report_date", "authors"],
    "additionalProperties": false,
    "$def": {
        "authors": {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "description": "author's name"
                },
                "institution": {
                    "type": "string",
                    "description": "author's institution"
                },
                "email": {
                    "type": "string",
                    "format": "email",
                    "description": "author's email"
                }
            },
            "additionalProperties": false,
            "required": ["name", "institution", "email"]
        }
    }
}





思考模式
DeepSeek 模型支持思考模式：在输出最终回答之前，模型会先输出一段思维链内容，以提升最终答案的准确性。您可以通过以下任意一种方式，开启思考模式：

设置 model 参数："model": "deepseek-reasoner"

设置 thinking 参数："thinking": {"type": "enabled"}

如果您使用的是 OpenAI SDK，在设置 thinking 参数时，需要将 thinking 参数传入 extra_body 中：

response = client.chat.completions.create(
  model="deepseek-chat",
  # ...
  extra_body={"thinking": {"type": "enabled"}}
)

API 参数
输入参数：

max_tokens：模型单次回答的最大长度（含思维链输出），默认为 32K，最大为 64K。
输出字段：

reasoning_content：思维链内容，与 content 同级，访问方法见样例代码。
content：最终回答内容。
tool_calls: 模型工具调用。
支持的功能：Json Output、Tool Calls、对话补全，对话前缀续写 (Beta)

不支持的功能：FIM 补全 (Beta)

不支持的参数：temperature、top_p、presence_penalty、frequency_penalty、logprobs、top_logprobs。请注意，为了兼容已有软件，设置 temperature、top_p、presence_penalty、frequency_penalty 参数不会报错，但也不会生效。设置 logprobs、top_logprobs 会报错。

多轮对话拼接
在每一轮对话过程中，模型会输出思维链内容（reasoning_content）和最终回答（content）。在下一轮对话中，之前轮输出的思维链内容不会被拼接到上下文中，如下图所示：


样例代码
下面的代码以 Python 语言为例，展示了如何访问思维链和最终回答，以及如何在多轮对话中进行上下文拼接。注意代码中在新一轮对话里，只传入了上一轮输出的 content，而忽略了 reasoning_content。

非流式
流式
from openai import OpenAI
client = OpenAI(api_key="<DeepSeek API Key>", base_url="https://api.deepseek.com")

# Turn 1
messages = [{"role": "user", "content": "9.11 and 9.8, which is greater?"}]
response = client.chat.completions.create(
    model="deepseek-reasoner",
    messages=messages,
    stream=True
)

reasoning_content = ""
content = ""

for chunk in response:
    if chunk.choices[0].delta.reasoning_content:
        reasoning_content += chunk.choices[0].delta.reasoning_content
    else:
        content += chunk.choices[0].delta.content

# Turn 2
messages.append({"role": "assistant", "content": content})
messages.append({'role': 'user', 'content': "How many Rs are there in the word 'strawberry'?"})
response = client.chat.completions.create(
    model="deepseek-reasoner",
    messages=messages,
    stream=True
)
# ...

工具调用
我们为 DeepSeek 模型的思考模式增加了工具调用功能。模型在输出最终答案之前，可以进行多轮的思考与工具调用，以提升答案的质量。其调用模式如下图所示：


在回答问题 1 过程中（请求 1.1 - 1.3），模型进行了多次思考 + 工具调用后给出答案。在这个过程中，用户需回传思维链内容（reasoning_content）给 API，以让模型继续思考。
在下一个用户问题开始时（请求 2.1），需删除之前的 reasoning_content，并保留其它内容发送给 API。如果保留了 reasoning_content 并发送给 API，API 将会忽略它们。
兼容性提示
因思考模式下的工具调用过程中要求用户回传 reasoning_content 给 API，若您的代码中未正确回传 reasoning_content，API 会返回 400 报错。正确回传方法请您参考下面的样例代码。

样例代码
下面是一个简单的在思考模式下进行工具调用的样例代码：

import os
import json
from openai import OpenAI

# The definition of the tools
tools = [
    {
        "type": "function",
        "function": {
            "name": "get_date",
            "description": "Get the current date",
            "parameters": { "type": "object", "properties": {} },
        }
    },
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "Get weather of a location, the user should supply the location and date.",
            "parameters": {
                "type": "object",
                "properties": {
                    "location": { "type": "string", "description": "The city name" },
                    "date": { "type": "string", "description": "The date in format YYYY-mm-dd" },
                },
                "required": ["location", "date"]
            },
        }
    },
]

# The mocked version of the tool calls
def get_date_mock():
    return "2025-12-01"

def get_weather_mock(location, date):
    return "Cloudy 7~13°C"

TOOL_CALL_MAP = {
    "get_date": get_date_mock,
    "get_weather": get_weather_mock
}

def clear_reasoning_content(messages):
    for message in messages:
        if hasattr(message, 'reasoning_content'):
            message.reasoning_content = None

def run_turn(turn, messages):
    sub_turn = 1
    while True:
        response = client.chat.completions.create(
            model='deepseek-chat',
            messages=messages,
            tools=tools,
            extra_body={ "thinking": { "type": "enabled" } }
        )
        messages.append(response.choices[0].message)
        reasoning_content = response.choices[0].message.reasoning_content
        content = response.choices[0].message.content
        tool_calls = response.choices[0].message.tool_calls
        print(f"Turn {turn}.{sub_turn}\n{reasoning_content=}\n{content=}\n{tool_calls=}")
        # If there is no tool calls, then the model should get a final answer and we need to stop the loop
        if tool_calls is None:
            break
        for tool in tool_calls:
            tool_function = TOOL_CALL_MAP[tool.function.name]
            tool_result = tool_function(**json.loads(tool.function.arguments))
            print(f"tool result for {tool.function.name}: {tool_result}\n")
            messages.append({
                "role": "tool",
                "tool_call_id": tool.id,
                "content": tool_result,
            })
        sub_turn += 1

client = OpenAI(
    api_key=os.environ.get('DEEPSEEK_API_KEY'),
    base_url=os.environ.get('DEEPSEEK_BASE_URL'),
)

# The user starts a question
turn = 1
messages = [{
    "role": "user",
    "content": "How's the weather in Hangzhou Tomorrow"
}]
run_turn(turn, messages)

# The user starts a new question
turn = 2
messages.append({
    "role": "user",
    "content": "How's the weather in Hangzhou Tomorrow"
})
# We recommended to clear the reasoning_content in history messages so as to save network bandwidth
clear_reasoning_content(messages)
run_turn(turn, messages)

在 Turn 1 的每个子请求中，都携带了该 Turn 下产生的 reasoning_content 给 API，从而让模型继续之前的思考。response.choices[0].message 携带了 assistant 消息的所有必要字段，包括 content、reasoning_content、tool_calls。简单起见，可以直接用如下代码将消息 append 到 messages 结尾：

messages.append(response.choices[0].message)

这行代码等价于：

messages.append({
    'role': 'assistant',
    'content': response.choices[0].message.content,
    'reasoning_content': response.choices[0].message.reasoning_content,
    'tool_calls': response.choices[0].message.tool_calls,
})

在 Turn 2 开始时，我们建议丢弃掉之前 Turn 中的 reasoning_content 来节省网络带宽：

clear_reasoning_content(messages)

该代码的样例输出如下：

Turn 1.1
reasoning_content="The user is asking about the weather in Hangzhou tomorrow. I need to get the current date first, then calculate tomorrow's date, and then call the weather API. Let me start by getting the current date."
content=''
tool_calls=[ChatCompletionMessageToolCall(id='call_00_Tcek83ZQ4fFb1RfPQnsPEE5w', function=Function(arguments='{}', name='get_date'), type='function', index=0)]
tool_result(get_date): 2025-12-01

Turn 1.2
reasoning_content='Today is December 1, 2025. Tomorrow is December 2, 2025. I need to format the date as YYYY-mm-dd: "2025-12-02". Now I can call get_weather with location Hangzhou and date 2025-12-02.'
content=''
tool_calls=[ChatCompletionMessageToolCall(id='call_00_V0Uwt4i63m5QnWRS1q1AO1tP', function=Function(arguments='{"location": "Hangzhou", "date": "2025-12-02"}', name='get_weather'), type='function', index=0)]
tool_result(get_weather): Cloudy 7~13°C

Turn 1.3
reasoning_content="I have the weather information: Cloudy with temperatures between 7 and 13°C. I should respond in a friendly, helpful manner. I'll mention that it's for tomorrow (December 2, 2025) and give the details. I can also ask if they need any other information. Let's craft the response."
content="Tomorrow (Tuesday, December 2, 2025) in Hangzhou will be **cloudy** with temperatures ranging from **7°C to 13°C**.  \n\nIt might be a good idea to bring a light jacket if you're heading out. Is there anything else you'd like to know about the weather?"
tool_calls=None

Turn 2.1
reasoning_content="The user wants clothing advice for tomorrow based on the weather in Hangzhou. I know tomorrow's weather: cloudy, 7-13°C. That's cool but not freezing. I should suggest layered clothing, maybe a jacket, long pants, etc. I can also mention that since it's cloudy, an umbrella might not be needed unless there's rain chance, but the forecast didn't mention rain. I should be helpful and give specific suggestions. I can also ask if they have any specific activities planned to tailor the advice. Let me respond."
content="Based on tomorrow's forecast of **cloudy weather with temperatures between 7°C and 13°C** in Hangzhou, here are some clothing suggestions:\n\n**Recommended outfit:**\n- **Upper body:** A long-sleeve shirt or sweater, plus a light to medium jacket (like a fleece, windbreaker, or light coat)\n- **Lower body:** Long pants or jeans\n- **Footwear:** Closed-toe shoes or sneakers\n- **Optional:** A scarf or light hat for extra warmth, especially in the morning and evening\n\n**Why this works:**\n- The temperature range is cool but not freezing, so layering is key\n- Since it's cloudy but no rain mentioned, you likely won't need an umbrella\n- The jacket will help with the morning chill (7°C) and can be removed if you warm up during the day\n\n**If you have specific plans:**\n- For outdoor activities: Consider adding an extra layer\n- For indoor/office settings: The layered approach allows you to adjust comfortably\n\nWould you like more specific advice based on your planned activities?"
tool_calls=None



简单结论： 在 Spring AI 目前的版本（以及大多数标准 OpenAI 客户端封装）中，直接使用 DeepSeek 的 推理模型（R1） 进行 Parallel Tool Calling 是不推荐且极难开箱即用的。

这里有三个核心障碍，验证了你的担忧：

1. reasoning_content 的“幽灵”字段问题
Spring AI 的底层（以及几乎所有 OpenAI 兼容的 SDK）是按照 OpenAI 的标准协议解析响应的。标准协议只有 content 和 tool_calls。

DeepSeek R1 的行为：它会先输出一段 reasoning_content（思维链），然后才是 content 或 tool_calls。

Spring AI 的反应：

如果使用流式（Stream），Spring AI 可能会把 reasoning_content 误判为普通的文本内容（content），或者直接丢弃（取决于 DeepSeek API 是否将其封装在标准 delta 中）。

关键问题：Spring AI 的 AssistantMessage 或 ChatMessage 对象目前没有标准字段来存储 reasoning_content。这意味着在多轮对话中，你无法自动将上一轮的“思考过程”回传给模型。

后果：模型会“失忆”。它忘了它刚才思考了什么，导致后续的 Tool Calling 逻辑断裂或幻觉。

2. 推理模型的天性 vs. 并行调用
Chat 模型 (V3)：是“直觉型”的。它看到问题，立刻判断“我要调 A 和 B 工具”，然后并发发出指令。这是 Parallel Tool Calling 的最佳场景。

Reasoning 模型 (R1)：是“深思熟虑型”的。它的核心机制是 CoT (Chain of Thought)。它倾向于串行思考。

它通常会想：“首先我要做 X，为了做 X 我需要调用工具 A……（等待 A 结果）……拿到结果后，我发现还需要做 Y，现在调用工具 B”。

强行要求 R1 输出 Parallel Tool Calls（一次性并行输出 A 和 B），往往会打断它的推理链条，或者导致它为了并行而并行，牺牲了推理的深度。

3. Spring AI 的 Tool Execution Loop 机制
Spring AI 内部有一个自动的 Tool Execution Loop（当你配置了 Call 模式而非单纯 Prompt 时）：

模型返回 Tool Call 意图。

Spring AI 拦截，执行 Java 方法。

Spring AI 将结果打包成 ToolMessage 塞回历史记录。

Spring AI 再次调用模型。

在 R1 上这个循环会崩溃： 因为第 1 步和第 4 步之间，R1 的 reasoning_content 丢失了（因为 Spring AI 的 Message 对象没存它）。当第 4 步再次请求 R1 时，R1 看到的历史是不完整的，它可能会重新开始推理，或者感到困惑，导致死循环或错误。

✅ 最佳解决方案：架构分离模式
正如我们在上一个问题中讨论的，不要试图让 R1 去做“调度员（Router）”，它不适合。

请采用“主从架构”，这是目前在 Spring AI 中使用推理模型最稳健的方案：

Router / Orchestrator (调度层)

模型：使用 DeepSeek V3 (Chat Model)。

能力：它完全支持 Parallel Tool Calling，响应快，完美兼容 Spring AI 的标准协议。

职责：负责理解用户意图，分发任务，聚合结果。

Specialized Tool (思考工具)

模型：使用 DeepSeek R1 (Reasoning Model)。

封装：将其封装为一个 Java Function (Tool)。

职责：只负责“思考”。

代码实现逻辑（Spring AI）：

Java

@Component
public class DeepReasoningTool {

    private final ChatModel r1ChatModel; // 专门配置 R1 的 Client

    public DeepReasoningTool(ChatModel r1ChatModel) {
        this.r1ChatModel = r1ChatModel;
    }

    @Tool(description = "当遇到极度复杂的问题，需要深度逻辑推演、长文本分析或复杂代码生成时，调用此工具。")
    public String performDeepThinking(String complexQuery) {
        // 这里我们不需要 Tool Calling，只需要 R1 的文本生成能力
        // R1 的 reasoning_content 会被这里的 Prompt 内部消化（或者拼接到返回结果中）
        String response = r1ChatModel.prompt()
            .user(complexQuery)
            .call()
            .content(); // 这里拿到的 content 已经是 R1 思考后的定论
        
        return response;
    }
}
主流程 (使用 V3)：

Java

ChatClient v3Client = ChatClient.builder(deepSeekV3Model)
    .defaultTools("deepReasoningTool", "universalCrudTool") // 同时挂载思考工具和CRUD工具
    .build();

// 用户问：“先分析一下如果不杀在这个章节反派会有什么剧情隐患，然后帮我把反派删了。”
// V3 的反应：
// 1. 识别到“分析剧情隐患” -> 属于复杂任务 -> 并行/串行调用 deepReasoningTool
// 2. 识别到“删了反派” -> 属于简单任务 -> 并行调用 universalCrudTool
总结
DeepSeek V3：完美支持 Parallel Tool Calling，在 Spring AI 中开箱即用。

DeepSeek R1：不要用于 Tool Calling。不仅因为 reasoning_content 的回传逻辑复杂（需要魔改 Spring AI 源码来存储这个字段），更因为它的推理属性本身就更适合做“被调用的专家”而不是“发号施令的调度员”。

建议： 坚持使用 V3 作为主对话模型来处理 Tool Calling，将 R1 封装为工具供 V3 调用。这是目前工程化落地的最优解。