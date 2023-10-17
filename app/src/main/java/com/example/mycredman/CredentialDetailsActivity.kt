package com.example.mycredman

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.startActivity
import com.example.mycredman.ui.theme.MyCredManTheme


class CredentialDetailsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent

        // Receive String type values
        val stringVal = intent.getStringExtra("ServiceName")?:""
        val stringValUrl = intent.getStringExtra("ServiceNameUrl")?:""
        val stringValId = intent.getStringExtra("ServiceNameId")?:""
        val stringcredentialId = intent.getByteArrayExtra("stringcredentialId")?: byteArrayOf()


        setContent {
            MyCredManTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CredentialEntry(stringVal, stringValUrl, stringValId, stringcredentialId)
                }
            }
        }
    }
}

@Composable
fun CredentialEntry(name: String, url: String, id: String, credentialId: ByteArray, modifier: Modifier = Modifier) {
    val openDialog = remember { mutableStateOf(false) }
    val context = LocalContext.current
    val intent = Intent(context,MainActivity::class.java)

    Column(
        verticalArrangement= Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
    )  {
        Text(text = name, fontSize = 30.sp, fontWeight = FontWeight.Bold,color = MaterialTheme.colorScheme.primary)
        Text(text = "URL", color = MaterialTheme.colorScheme.secondary)
        Text(text = url, Modifier.padding(start = 12.dp))
        Text(text = "ID", color = MaterialTheme.colorScheme.secondary)
        Text(text = id, Modifier.padding(start = 12.dp))
        Button(onClick = {
            openDialog.value = true

        },
            colors = ButtonDefaults.buttonColors(Color(0xFFEB5505), Color.White)
        ) {

            Text(text = "Delete")
        }
    }


    if (openDialog.value) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.background,
            onDismissRequest = {
                // Dismiss the dialog when the user clicks outside the dialog or on the back
                // button. If you want to disable that functionality, simply use an empty
                // onDismissRequest.
                openDialog.value = false
            },
            //   icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
            //    title = {
            //       Text(text = "Title")
            //  },
            text = {
                Text(
                    text = "Are you sure to delete?",
                    color = MaterialTheme.colorScheme.primary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        openDialog.value = false
                       // Log.d("MyCredMan",credentialId)
                        //delect credential
                        MyCredentialDataManager.delete(context,url,credentialId)
                        startActivity(context,intent,null)


                    },colors = ButtonDefaults.textButtonColors(Color(0xFFEB5505), Color.White)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        openDialog.value = false
                    },colors = ButtonDefaults.buttonColors(Color.Transparent, Color.Gray)
                ) {
                    Text("Cancel")
                }
            }
        )
    }

}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyCredManTheme {
        CredentialEntry("Android", "example.com", "apple", byteArrayOf())
    }
}