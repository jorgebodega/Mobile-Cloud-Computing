package fi.aalto.jorgebodega.hellouser;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private String name = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final EditText namebox = (EditText) findViewById(R.id.txtInput);
        Button button = (Button) findViewById(R.id.btnSubmit);
        final TextView textView = (TextView) findViewById(R.id.txtResult);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                name = "Hello " + namebox.getText().toString();
                namebox.setText("");
                textView.setText(name);
            }
        });
    }
}
