#!/usr/bin/env node
// MCP echo server（Day 16 联调靶子）：add(a,b)、echo(text) 两个工具。
// 换行分隔 JSON-RPC 2.0：读一行请求，写一行响应。启动方式：node examples/mcp-echo.js
const readline = require('readline');
const rl = readline.createInterface({ input: process.stdin });
const send = (o) => process.stdout.write(JSON.stringify(o) + '\n');

const tools = [
  { name: 'add', description: '两个整数相加',
    inputSchema: { type: 'object', properties: { a: { type: 'number' }, b: { type: 'number' } }, required: ['a', 'b'] } },
  { name: 'echo', description: '原样返回文本',
    inputSchema: { type: 'object', properties: { text: { type: 'string' } }, required: ['text'] } }
];

rl.on('line', (line) => {
  let msg;
  try {
    msg = JSON.parse(line);
  } catch (e) {
    return;
  }
  if (msg.method === 'initialize') {
    send({ jsonrpc: '2.0', id: msg.id, result: { protocolVersion: '2024-11-05',
      capabilities: { tools: {} }, serverInfo: { name: 'mcp-echo', version: '0.1.0' } } });
  } else if (msg.method === 'notifications/initialized') {
    // 通知无需响应
  } else if (msg.method === 'tools/list') {
    send({ jsonrpc: '2.0', id: msg.id, result: { tools } });
  } else if (msg.method === 'tools/call') {
    const { name, arguments: a } = msg.params;
    let text;
    if (name === 'add') text = String(a.a + a.b);
    else if (name === 'echo') text = a.text;
    else text = 'unknown tool: ' + name;
    send({ jsonrpc: '2.0', id: msg.id, result: { content: [{ type: 'text', text }] } });
  }
});
