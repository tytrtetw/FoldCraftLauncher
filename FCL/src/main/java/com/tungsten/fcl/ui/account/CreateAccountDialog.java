package com.tungsten.fcl.ui.account;

import static com.tungsten.fcl.setting.Accounts.FACTORY_AUTHLIB_INJECTOR;
import static com.tungsten.fcl.setting.Accounts.FACTORY_MICROSOFT;
import static com.tungsten.fcl.setting.Accounts.FACTORY_OFFLINE;
import static com.tungsten.fcl.setting.ConfigHolder.config;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.tabs.TabLayout;
import com.tungsten.fcl.R;
import com.tungsten.fcl.game.OAuthServer;
import com.tungsten.fcl.game.TexturesLoader;
import com.tungsten.fcl.setting.Accounts;
import com.tungsten.fcl.ui.UIManager;
import com.tungsten.fcl.util.AndroidUtils;
import com.tungsten.fcl.util.FXUtils;
import com.tungsten.fcl.util.WeakListenerHolder;
import com.tungsten.fclcore.auth.AccountFactory;
import com.tungsten.fclcore.auth.CharacterSelector;
import com.tungsten.fclcore.auth.NoSelectedCharacterException;
import com.tungsten.fclcore.auth.authlibinjector.AuthlibInjectorAccountFactory;
import com.tungsten.fclcore.auth.authlibinjector.AuthlibInjectorServer;
import com.tungsten.fclcore.auth.microsoft.MicrosoftAccountFactory;
import com.tungsten.fclcore.auth.offline.OfflineAccountFactory;
import com.tungsten.fclcore.auth.yggdrasil.GameProfile;
import com.tungsten.fclcore.auth.yggdrasil.YggdrasilService;
import com.tungsten.fclcore.fakefx.beans.property.ObjectProperty;
import com.tungsten.fclcore.fakefx.beans.property.SimpleObjectProperty;
import com.tungsten.fclcore.task.Schedulers;
import com.tungsten.fclcore.task.Task;
import com.tungsten.fclcore.task.TaskExecutor;
import com.tungsten.fclcore.util.StringUtils;
import com.tungsten.fcllibrary.component.FCLAdapter;
import com.tungsten.fcllibrary.component.dialog.FCLAlertDialog;
import com.tungsten.fcllibrary.component.dialog.FCLDialog;
import com.tungsten.fcllibrary.component.view.FCLButton;
import com.tungsten.fcllibrary.component.view.FCLEditText;
import com.tungsten.fcllibrary.component.view.FCLImageButton;
import com.tungsten.fcllibrary.component.view.FCLTabLayout;
import com.tungsten.fcllibrary.component.view.FCLTextView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class CreateAccountDialog extends FCLDialog implements View.OnClickListener, TabLayout.OnTabSelectedListener {

    private static CreateAccountDialog instance;

    private FCLTextView title;
    private FCLTabLayout tabLayout;
    private RelativeLayout detailsContainer;
    private FCLButton login;
    private FCLButton cancel;

    private boolean showMethodSwitcher;
    private AccountFactory<?> factory;
    private TaskExecutor loginTask;
    private Details details;
    private final ObjectProperty<OAuthServer.GrantDeviceCodeEvent> deviceCode = new SimpleObjectProperty<>();

    public static CreateAccountDialog getInstance() {
        return instance;
    }

    public CreateAccountDialog(@NonNull Context context, AccountFactory<?> factory) {
        super(context);
        instance = this;
        setContentView(R.layout.dialog_create_account);
        setCancelable(false);
        title = findViewById(R.id.title);
        tabLayout = findViewById(R.id.tab_layout);
        detailsContainer = findViewById(R.id.detail_container);
        login = findViewById(R.id.login);
        cancel = findViewById(R.id.cancel);
        login.setOnClickListener(this);
        cancel.setOnClickListener(this);
        init(factory);
    }

    private void init(AccountFactory<?> factory) {
        if (factory == null) {
            showMethodSwitcher = true;
            String preferred = config().getPreferredLoginType();
            try {
                factory = Accounts.getAccountFactory(preferred);
            } catch (IllegalArgumentException e) {
                factory = FACTORY_OFFLINE;
            }
        } else {
            showMethodSwitcher = false;
        }
        this.factory = factory;

        int titleId;
        if (showMethodSwitcher) {
            titleId = R.string.account_create;
        } else {
            if (factory instanceof OfflineAccountFactory) {
                titleId = R.string.account_create_offline;
            } else if (factory instanceof MicrosoftAccountFactory) {
                titleId = R.string.account_create_microsoft;
            }
            else {
                titleId = R.string.account_create_external;
            }
        }
        title.setText(getContext().getString(titleId));
        tabLayout.setVisibility(showMethodSwitcher ? View.VISIBLE : View.GONE);
        tabLayout.addOnTabSelectedListener(this);

        initDetails();
    }

    private void initDetails() {
        if (factory instanceof OfflineAccountFactory) {
            details = new OfflineDetails(getContext());
        }
        if (factory instanceof MicrosoftAccountFactory) {
            details = new MicrosoftDetails(getContext());
        }
        if (factory instanceof AuthlibInjectorAccountFactory) {
            details = new ExternalDetails(getContext());
        }
        detailsContainer.removeAllViews();
        detailsContainer.addView(details.getView(), ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void login() {
        login.setEnabled(false);
        cancel.setEnabled(false);

        String username;
        String password;
        Object additionalData;
        try {
            username = details.getUsername();
            password = details.getPassword();
            additionalData = details.getAdditionalData();
        } catch (IllegalStateException e) {
            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
            login.setEnabled(true);
            cancel.setEnabled(true);
            return;
        }

        deviceCode.set(null);

        CharacterSelector selector = new DialogCharacterSelector(getContext());
        loginTask = Task.supplyAsync(() -> factory.create(selector, username, password, null, additionalData))
                .whenComplete(Schedulers.androidUIThread(), account -> {
                    int oldIndex = Accounts.getAccounts().indexOf(account);
                    if (oldIndex == -1) {
                        Accounts.getAccounts().add(account);
                    } else {
                        // adding an already-added account
                        // instead of discarding the new account, we first remove the existing one then add the new one
                        Accounts.getAccounts().remove(oldIndex);
                        Accounts.getAccounts().add(oldIndex, account);
                    }

                    // select the new account
                    Accounts.setSelectedAccount(account);

                    login.setEnabled(true);
                    cancel.setEnabled(true);
                    UIManager.getInstance().getAccountUI().refresh().start();
                    dismiss();
                }, exception -> {
                    if (exception instanceof NoSelectedCharacterException) {
                        dismiss();
                    } else {
                        FCLAlertDialog.Builder builder = new FCLAlertDialog.Builder(getContext());
                        builder.setAlertLevel(FCLAlertDialog.AlertLevel.ALERT);
                        builder.setMessage(Accounts.localizeErrorMessage(getContext(), exception));
                        builder.setCancelable(false);
                        builder.setNegativeButton(getContext().getString(com.tungsten.fcllibrary.R.string.dialog_positive), null);
                        builder.create().show();
                    }
                    login.setEnabled(true);
                    cancel.setEnabled(true);
                }).executor(true);
    }

    private void onCancel() {
        if (loginTask != null) {
            loginTask.cancel();
        }
        dismiss();
    }

    @Override
    public void onClick(View view) {
        if (view == login) {
            login();
        }
        if (view == cancel) {
            onCancel();
        }
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        AccountFactory<?> newMethod = FACTORY_OFFLINE;
        if (tab.getPosition() == 1) {
            newMethod = FACTORY_MICROSOFT;
        }
        if (tab.getPosition() == 2) {
            newMethod = FACTORY_AUTHLIB_INJECTOR;
        }
        config().setPreferredLoginType(Accounts.getLoginType(newMethod));
        this.factory = newMethod;
        initDetails();
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {

    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {

    }

    // details panel
    private interface Details {
        String getUsername();
        String getPassword();
        Object getAdditionalData();
        View getView();
    }

    private static class OfflineDetails implements Details {

        private final Context context;
        private final View view;

        private final FCLEditText username;

        public OfflineDetails(Context context) {
            this.context = context;
            this.view = LayoutInflater.from(context).inflate(R.layout.view_create_account_offline, null);
            username = view.findViewById(R.id.username);
        }

        @Override
        public String getUsername() throws IllegalStateException {
            if (StringUtils.isBlank(username.getText().toString())) {
                throw new IllegalStateException(context.getString(R.string.account_create_alert));
            }
            return username.getText().toString();
        }

        @Override
        public String getPassword() throws IllegalStateException {
            return null;
        }

        @Override
        public Object getAdditionalData() throws IllegalStateException {
            return null;
        }

        @Override
        public View getView() throws IllegalStateException {
            return view;
        }
    }

    private static class MicrosoftDetails implements Details {

        private final Context context;
        private final View view;

        private final WeakListenerHolder holder = new WeakListenerHolder();

        public MicrosoftDetails(Context context) {
            this.context = context;
            this.view = LayoutInflater.from(context).inflate(R.layout.view_create_account_microsoft, null);

            Handler handler = new Handler();
            FXUtils.onChangeAndOperate(CreateAccountDialog.getInstance().deviceCode, deviceCode -> handler.post(() -> {
                if (deviceCode != null) {
                    AndroidUtils.copyText(context, deviceCode.getUserCode());
                }
            }));
            holder.add(Accounts.OAUTH_CALLBACK.onGrantDeviceCode.registerWeak(value -> CreateAccountDialog.getInstance().deviceCode.set(value)));
        }

        @Override
        public String getUsername() throws IllegalStateException {
            return null;
        }

        @Override
        public String getPassword() throws IllegalStateException {
            return null;
        }

        @Override
        public Object getAdditionalData() throws IllegalStateException {
            return null;
        }

        @Override
        public View getView() throws IllegalStateException {
            return view;
        }
    }

    private static class ExternalDetails implements Details, View.OnClickListener {

        private static final String[] ALLOWED_LINKS = { "homepage", "register" };

        private final Context context;
        private final View view;

        private FCLTextView serverName;
        private FCLImageButton home;
        private FCLImageButton register;
        private FCLImageButton setting;
        private final FCLEditText username;
        private final FCLEditText password;

        public ExternalDetails(Context context) {
            this.context = context;
            this.view = LayoutInflater.from(context).inflate(R.layout.view_create_account_external, null);

            serverName = view.findViewById(R.id.server_name);
            home = view.findViewById(R.id.home);
            register = view.findViewById(R.id.register);
            setting = view.findViewById(R.id.setting);
            username = view.findViewById(R.id.username);
            password = view.findViewById(R.id.password);

            setting.setOnClickListener(this);

            refreshAuthenticateServer(config().getAuthlibInjectorServers().size() == 0 ? null : config().getAuthlibInjectorServers().get(0));
        }

        private void refreshAuthenticateServer(AuthlibInjectorServer authlibInjectorServer) {
            if (authlibInjectorServer == null) {
                serverName.setText(context.getString(R.string.account_create_server_not_select));
                home.setVisibility(View.GONE);
                register.setVisibility(View.GONE);
            }
            else {
                serverName.setText(authlibInjectorServer.getName());
                Map<String, String> links = authlibInjectorServer.getLinks();
                if (links.get("homepage") != null) {
                    home.setVisibility(View.VISIBLE);
                    home.setOnClickListener(view -> {
                        Uri uri = Uri.parse(links.get("homepage"));
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        context.startActivity(intent);
                    });
                } else {
                    home.setVisibility(View.GONE);
                }
                if (links.get("register") != null) {
                    register.setVisibility(View.VISIBLE);
                    register.setOnClickListener(view -> {
                        Uri uri = Uri.parse(links.get("register"));
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        context.startActivity(intent);
                    });
                } else {
                    register.setVisibility(View.GONE);
                }
            }
        }

        @Override
        public String getUsername() throws IllegalStateException {
            if (StringUtils.isBlank(username.getText().toString())) {
                throw new IllegalStateException(context.getString(R.string.account_create_alert));
            }
            return username.getText().toString();
        }

        @Override
        public String getPassword() throws IllegalStateException {
            if (StringUtils.isBlank(password.getText().toString())) {
                throw new IllegalStateException(context.getString(R.string.account_create_alert));
            }
            return password.getText().toString();
        }

        @Override
        public Object getAdditionalData() throws IllegalStateException {
            return null;
        }

        @Override
        public View getView() throws IllegalStateException {
            return view;
        }

        @Override
        public void onClick(View view) {
            if (view == setting) {

            }
        }
    }

    // character selector
    private static class DialogCharacterSelector extends FCLDialog implements CharacterSelector, View.OnClickListener {

        private final Handler handler;

        private final ListView listView;
        private final FCLButton cancel;

        private final CountDownLatch latch = new CountDownLatch(1);
        private GameProfile selectedProfile = null;

        public DialogCharacterSelector(Context context) {
            super(context);
            setContentView(R.layout.dialog_character_selector);
            setCancelable(false);
            handler = new Handler();
            listView = findViewById(R.id.list);
            cancel = findViewById(R.id.negative);
            cancel.setOnClickListener(this);
        }

        public void refresh(YggdrasilService service, List<GameProfile> profiles) {
            Adapter adapter = new Adapter(getContext(), service, profiles, profile -> {
                selectedProfile = profile;
                latch.countDown();
            });
            listView.setAdapter(adapter);
        }

        @Override
        public GameProfile select(YggdrasilService service, List<GameProfile> profiles) throws NoSelectedCharacterException {
            handler.post(() -> {
                refresh(service, profiles);
                show();
            });

            try {
                latch.await();

                if (selectedProfile == null)
                    throw new NoSelectedCharacterException();

                return selectedProfile;
            } catch (InterruptedException ignored) {
                throw new NoSelectedCharacterException();
            } finally {
                dismiss();
            }
        }

        @Override
        public void onClick(View view) {
            if (view == cancel) {
                latch.countDown();
                dismiss();
            }
        }

        private static class Adapter extends FCLAdapter {

            private final YggdrasilService service;
            private final List<GameProfile> profiles;
            private final Listener listener;

            public Adapter(Context context, YggdrasilService service, List<GameProfile> profiles, Listener listener) {
                super(context);
                this.service = service;
                this.profiles = profiles;
                this.listener = listener;
            }

            static class ViewHolder {
                ConstraintLayout parent;
                AppCompatImageView avatar;
                FCLTextView name;
            }

            interface Listener {
                void onSelect(GameProfile profile);
            }

            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                final ViewHolder viewHolder;
                if (view == null) {
                    viewHolder = new ViewHolder();
                    view = LayoutInflater.from(getContext()).inflate(R.layout.item_character, null);
                    viewHolder.parent = view.findViewById(R.id.parent);
                    viewHolder.avatar = view.findViewById(R.id.avatar);
                    viewHolder.name = view.findViewById(R.id.name);
                    view.setTag(viewHolder);
                } else {
                    viewHolder = (ViewHolder) view.getTag();
                }
                GameProfile gameProfile = profiles.get(i);
                viewHolder.name.setText(gameProfile.getName());
                viewHolder.avatar.setBackground(TexturesLoader.avatarBinding(service, gameProfile.getId(), 32).get());
                viewHolder.parent.setOnClickListener(view1 -> {
                    listener.onSelect(gameProfile);
                });
                return view;
            }
        }
    }
}
