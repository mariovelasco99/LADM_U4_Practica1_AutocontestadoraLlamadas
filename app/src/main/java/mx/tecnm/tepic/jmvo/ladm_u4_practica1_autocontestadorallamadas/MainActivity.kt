package mx.tecnm.tepic.jmvo.ladm_u4_practica1_autocontestadorallamadas

import android.annotation.SuppressLint
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.telephony.SmsManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity() {

    val baseDatos = FirebaseFirestore.getInstance()
    var listaTelefonos = ArrayList<String>()
    var listaDatos = ArrayList<String>()

    val siLecturaLlamadas = 1
    val siEnviarMensajes = 2
    var hilo = Hilo(this)
    var Inicio = true
    var IdLlamada = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Permiso leer historial de llamadas
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.READ_CALL_LOG), siLecturaLlamadas)
        }

        //CONSULTA BD
        baseDatos.collection("contactos")
            .addSnapshotListener { querySnapshot, error ->

                if (error != null){
                    mensaje("ERROR: ${error.message!!}")
                    return@addSnapshotListener
                }

                listaDatos.clear()
                for (document in querySnapshot!!){
                    var cadena = "${document.getString("nombre")}&" +
                            "${document.getString("telefono")}&" +
                            "${document.getString("tipo")}"

                    listaDatos.add(cadena)

                }
                //listaViewLlamadas.adapter = ArrayAdapter<String>(this,android.R.layout.simple_expandable_list_item_1,listaTelefonos)
                //MOSTRAMOS POR DEFAULT LA LISTA BLANCA QUE TENEMOS EN FIREBASE
                mostrarContactos("Lista Blanca")
            }//baseDatos.collection


        btnActivar.setOnClickListener {
            //SI YA TENEMOS LOS PERMISOS DE LAS LLAMADAS
            //PODEMOS USAR LA FUNCION DE LAS LLAMADAS RECIENTES
            mostrarLlamadasRecientes()

            //PERMISO PARA ENVIAR LOS MENSAJES DE TEXTO (SMS)
           if(ActivityCompat.checkSelfPermission(this,
                    android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.SEND_SMS),siEnviarMensajes)
            }
            //INICIA EL HILO
            hilo.start()
        }//btnActivar

        btnAgregar.setOnClickListener {
            insertarContacto()
        }//btnAgregar

        btnVerListaB.setOnClickListener {
            mostrarContactos("Lista Blanca")
        }//btnVerListaB

        btnVerListaN.setOnClickListener {
            mostrarContactos("Lista Negra")
        }//btnVerListaB

    }

    @SuppressLint("Range")
    private fun mostrarLlamadasRecientes(){
        var llamadas = ArrayList<String>()
        val seleccion = CallLog.Calls.TYPE + "=" + CallLog.Calls.MISSED_TYPE
        var cursor = contentResolver.query(
            Uri.parse("content://call_log/calls"),
            null, seleccion, null, "date DESC")

        listaTelefonos.clear()
        var registro = ""
        while (cursor!!.moveToNext()){
            var nombre = cursor.getString(cursor.getColumnIndex(CallLog.Calls.CACHED_NAME))
            var telefono = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER))
            telefono = telefono.replace(" ".toRegex(), "")

            registro = "NOMBRE: ${nombre} \nNUMERO: ${telefono}"
            llamadas.add(registro)
            listaTelefonos.add(telefono)
        }
        listaViewLlamadas.adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, llamadas)
        cursor.close()
    } //mostrarLlamadasRecientes

    private fun mostrarContactos(tipoLista :String) {
        listaTelefonos.clear()

        for (i in 0..(listaDatos.size-1)) {
            var datos= listaDatos.get(i).split("&")

            if (datos[2] == tipoLista) {

                var cadena = "Nombre: ${datos[0]}" +
                        "\nTelefono: ${datos[1]}"

                listaTelefonos.add(cadena)
            }
        }

        if (listaTelefonos.isEmpty()){
            listaTelefonos.add("NO HAY DATOS EN LA LISTA")
        }

        listaViewLlamadas.adapter = ArrayAdapter<String>(this,android.R.layout.simple_expandable_list_item_1,listaTelefonos)
    }//mostrarContactos

    private fun insertarContacto(){
        var tipo = ""

        if(rbtnListBlanca.isChecked){
            tipo = rbtnListBlanca.text.toString()
        } else {
            tipo = rbtnListNegra.text.toString()
        }//else

        var datosInsertar = hashMapOf(
            "nombre" to txtNombre.text.toString(),
            "telefono" to txtTelefono.text.toString(),
            "tipo" to tipo
        )

        baseDatos.collection("contactos")
            .add(datosInsertar)
            .addOnSuccessListener {
                mensaje("EXITO! SE HA INSERTADO CORRECTAMENTE")
                limpiarCampos()
            }
            .addOnFailureListener {
                mensaje("ERROR!!NO SE LOGRO INSERTAR: ${it.message!!}")
            }
    }//insertarContacto

    private fun limpiarCampos() {
        txtNombre.setText("")
        txtTelefono.setText("")
    }



    public fun enviarSMS(nom :String, cel :String, tip :String) {
        var Mensaje = "NO DEVOLVERE TU LLAMADA, POR FAVOR NO INSISTAS"

        if (!tip.equals("Lista Negra")){
             Mensaje = "¡HOLA! ESTOY OCUPADO, ESTARE LIBRE HASTA LAS 15:00, TE DEVUELVO LA LLAMADA"

        } else {

            //GUARDAMOS EN FIREBASE como "Desconocido"
                // POR SI NOS MARCA UN NUM QUE NO HEMOS REGISTRADO

            var datosInsertar = hashMapOf(
                "nombre" to nom,
                "telefono" to cel,
                "tipo" to "Desconocido")

            baseDatos.collection("contactos")
                .add(datosInsertar)
                .addOnSuccessListener {

                }
                .addOnFailureListener {

                }
        }//else

        //ENVIAR EL MENSAJE SMS
        SmsManager.getDefault().sendTextMessage(cel,null,
            Mensaje,null,null)

        Toast.makeText(this,"SE ENVIO EL SMS",Toast.LENGTH_LONG)
            .show()
    }//enviarSMS


    private fun mensaje(s: String) {
        AlertDialog.Builder(this)
            .setTitle("ATENCION!!")
            .setMessage(s)
            .setPositiveButton("OK"){d, i-> }
            .show()
    }


    public fun checar() {
        var Cursor = contentResolver.query(CallLog.Calls.CONTENT_URI,
            null,null,null,"date DESC")

        if(Cursor!!.moveToFirst()){
            var Num_Telefono = Cursor.getColumnIndex(CallLog.Calls.NUMBER)
            var Tipo_Llamada = Cursor.getColumnIndex(CallLog.Calls.TYPE)
            var ID = Cursor.getColumnIndex(CallLog.Calls._ID)

            if(Inicio){
                IdLlamada = Cursor.getString(ID)
                Inicio = false
            }

            if (Cursor.getString(ID) != IdLlamada){
                // El Tipo "3" corresponde a las llamadas perdidas o no contestadas
                if (Cursor.getInt(Tipo_Llamada) == 3){
                    for (i in 0..(listaDatos.size-1)){
                        var datos= listaDatos.get(i).split("&")
                        System.out.println(datos[0]+","+datos[1]+","+datos[2])

                        //Si el numero de telefono coincide con alguno de nuestras listas
                        if(Cursor.getString(Num_Telefono) == datos[1]){
                            enviarSMS(datos[0],datos[1],datos[2])
                            break
                        }//if
                    }//for
                }//if
                IdLlamada = Cursor.getString(ID)
            }
        }
    }//cargarListaLlamadas
}

class Hilo(p:MainActivity) : Thread(){

    val Puntero = p

    override fun run() {
        super.run()
        while (true){
            sleep(1000)
            Puntero.runOnUiThread {
                Puntero.checar()
            }
        }
    }
}//class Hilo