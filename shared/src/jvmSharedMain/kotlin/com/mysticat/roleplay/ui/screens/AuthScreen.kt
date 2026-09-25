package com.mysticat.roleplay.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.mysticat.roleplay.ui.WhaleChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mysticat.roleplay.data.Repository
import com.mysticat.roleplay.ui.NoticeLine

/** 登录 / 注册 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(onLoggedIn: () -> Unit) {
    var mode by remember { mutableStateOf("login") } // "login" | "register"
    var nickname by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Lock, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("鲸鱼", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "AI 角色扮演",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WhaleChip(selected = mode == "login", onClick = { mode = "login"; error = null }, label = { Text("登录") })
                WhaleChip(selected = mode == "register", onClick = { mode = "register"; error = null }, label = { Text("注册") })
            }
            Spacer(Modifier.height(20.dp))

            if (mode == "register") {
                OutlinedTextField(
                    value = nickname, onValueChange = { nickname = it },
                    label = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
            }
            OutlinedTextField(
                value = account, onValueChange = { account = it },
                label = { Text("账号") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("密码") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    // 密码校验/哈希是 PBKDF2 12 万次迭代（数百毫秒~秒级），**必须离开主线程**：
                    // 以前整段同步跑在 onClick 里 —— 界面卡住、进度圈永远不显示（busy 在同一个主线程
                    // 执行内 true→false，重组时读到的永远是 false），低端机还会 ANR。
                    if (busy) return@Button   // 异步化后仍要防重复点击（原来靠主线程阻塞天然防住）
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            // 计算型任务放 Default：PBKDF2 不吃 IO，别占着 IO 线程池
                            withContext(Dispatchers.Default) {
                                if (mode == "login") Repository.login(account, password)
                                else Repository.createAccount(nickname, account, password)
                            }
                            onLoggedIn()
                        } catch (t: Throwable) {
                            error = t.message ?: "操作失败"
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && account.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (mode == "login") "登录" else "注册")
                }
            }

            Spacer(Modifier.height(18.dp))
            AuthSecurityNotice()
        }
    }
}

@Composable
private fun AuthSecurityNotice() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "测试版安全须知",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            NoticeLine("这是本地测试版，数据只存在本机，不上传云端。")
            NoticeLine("密码加盐哈希存储；API Key 已在本地加密。")
            NoticeLine("仅支持 https 接口，请只填入正规模型服务商。")
            NoticeLine("对话与创作内容会发送到你填写的模型服务商。")
        }
    }
}

