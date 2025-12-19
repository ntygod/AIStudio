import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Button } from '../components/ui/button';
import { ArrowRight, Mail, Lock, Globe, PenTool, Smartphone, QrCode, User, Hash, ArrowLeft, Loader2, AlertCircle } from 'lucide-react';
import { useAuthStore } from '@/stores/auth-store';

interface LoginPageProps {
    onLogin: () => void;
}

type Lang = 'zh' | 'en';
type AuthMode = 'login' | 'register' | 'forgot-password';
type LoginMethod = 'password' | 'mobile' | 'wechat';
type RegisterMethod = 'mobile' | 'email';

interface FormData {
    identifier: string;
    password: string;
    confirmPassword: string;
    username: string;
    email: string;
    mobile: string;
    code: string;
}

interface FormErrors {
    identifier?: string;
    password?: string;
    confirmPassword?: string;
    username?: string;
    email?: string;
    mobile?: string;
    code?: string;
    general?: string;
}

const translations = {
    zh: {
        quote: "文字是流淌于虚空的灵魂之墨。",
        author: "— InkFlow AI",
        slogan: "让想象力随 AI 流淌",
        welcomeBack: "欢迎回来",
        joinUs: "开启创作之旅",
        resetPass: "重置密码",
        usernameLabel: "用户名 / 邮箱",
        usernamePlaceholder: "请输入用户名或邮箱",
        passwordLabel: "密码",
        passwordPlaceholder: "请输入密码",
        confirmPasswordLabel: "确认密码",
        confirmPasswordPlaceholder: "请再次输入密码",
        mobileLabel: "手机号",
        mobilePlaceholder: "请输入手机号",
        emailLabel: "邮箱",
        emailPlaceholder: "请输入邮箱地址",
        codeLabel: "验证码",
        codePlaceholder: "请输入验证码",
        loginBtn: "进入工作室",
        createBtn: "立即注册",
        resetBtn: "发送重置链接",
        sendCode: "发送验证码",
        sent: "已发送",
        forgotPass: "忘记密码？",
        noAccount: "还没有账号？ 立即注册",
        hasAccount: "已有账号？ 直接登录",
        backToLogin: "返回登录",
        methodPassword: "密码登录",
        methodMobile: "验证码登录",
        methodWechat: "微信扫码",
        methodMobileReg: "手机注册",
        methodEmailReg: "邮箱注册",
        wechatScan: "请使用微信扫描二维码登录",
        footer: "INKFLOW V2.0 • AI 驱动的创作引擎",
        // Validation messages
        required: "此字段为必填项",
        invalidEmail: "请输入有效的邮箱地址",
        passwordTooShort: "密码至少需要 8 个字符",
        passwordWeak: "密码强度：弱",
        passwordMedium: "密码强度：中",
        passwordStrong: "密码强度：强",
        passwordMismatch: "两次输入的密码不一致",
        usernameTooShort: "用户名至少需要 3 个字符",
        loginFailed: "登录失败，请检查用户名和密码",
        registerFailed: "注册失败，请稍后重试",
        networkError: "网络连接失败，请检查网络",
        passwordRequirements: "密码需包含大小写字母和数字",
    },
    en: {
        quote: "Words are the ink of the soul, flowing through the void.",
        author: "— InkFlow AI",
        slogan: "Where Imagination Flows with AI.",
        welcomeBack: "Welcome Back",
        joinUs: "Start Creating",
        resetPass: "Reset Password",
        usernameLabel: "Username / Email",
        usernamePlaceholder: "Enter username or email",
        passwordLabel: "Password",
        passwordPlaceholder: "Enter password",
        confirmPasswordLabel: "Confirm Password",
        confirmPasswordPlaceholder: "Re-enter your password",
        mobileLabel: "Mobile Number",
        mobilePlaceholder: "Enter mobile number",
        emailLabel: "Email",
        emailPlaceholder: "Enter email address",
        codeLabel: "Verification Code",
        codePlaceholder: "Enter verification code",
        loginBtn: "Enter Studio",
        createBtn: "Create Account",
        resetBtn: "Send Reset Link",
        sendCode: "Send Code",
        sent: "Sent",
        forgotPass: "Forgot Password?",
        noAccount: "New here? Create an account",
        hasAccount: "Already have an account? Log in",
        backToLogin: "Back to Login",
        methodPassword: "Password",
        methodMobile: "Mobile Code",
        methodWechat: "WeChat",
        methodMobileReg: "Mobile",
        methodEmailReg: "Email",
        wechatScan: "Scan QR Code with WeChat to Log In",
        footer: "INKFLOW V2.0 • AI-POWERED WRITING",
        // Validation messages
        required: "This field is required",
        invalidEmail: "Please enter a valid email address",
        passwordTooShort: "Password must be at least 8 characters",
        passwordWeak: "Password strength: Weak",
        passwordMedium: "Password strength: Medium",
        passwordStrong: "Password strength: Strong",
        passwordMismatch: "Passwords do not match",
        usernameTooShort: "Username must be at least 3 characters",
        loginFailed: "Login failed, please check your credentials",
        registerFailed: "Registration failed, please try again",
        networkError: "Network error, please check your connection",
        passwordRequirements: "Password must contain uppercase, lowercase and numbers",
    }
};

const initialFormData: FormData = {
    identifier: '',
    password: '',
    confirmPassword: '',
    username: '',
    email: '',
    mobile: '',
    code: '',
};

// Password strength calculation
type PasswordStrength = 'weak' | 'medium' | 'strong';

function getPasswordStrength(password: string): PasswordStrength {
    if (password.length < 8) return 'weak';
    
    const hasLower = /[a-z]/.test(password);
    const hasUpper = /[A-Z]/.test(password);
    const hasNumber = /[0-9]/.test(password);
    const hasSpecial = /[!@#$%^&*(),.?":{}|<>]/.test(password);
    
    // 后端要求：必须同时包含大小写字母和数字
    const meetsMinRequirements = hasLower && hasUpper && hasNumber;
    
    if (!meetsMinRequirements) return 'weak';
    
    // 有特殊字符且长度>=10 为强
    if (hasSpecial && password.length >= 10) return 'strong';
    // 满足基本要求为中
    return 'medium';
}

export function LoginPage({ onLogin }: LoginPageProps) {
    const [authMode, setAuthMode] = useState<AuthMode>('login');
    const [loginMethod, setLoginMethod] = useState<LoginMethod>('password');
    const [registerMethod, setRegisterMethod] = useState<RegisterMethod>('email');
    const [lang, setLang] = useState<Lang>('zh');
    const [countDown, setCountDown] = useState(0);
    const [formData, setFormData] = useState<FormData>(initialFormData);
    const [errors, setErrors] = useState<FormErrors>({});

    // Connect to AuthStore
    const { login, register, isLoading, error: authError, clearError } = useAuthStore();

    const t = translations[lang];

    // Clear errors when switching modes
    useEffect(() => {
        setErrors({});
        clearError();
        setFormData(initialFormData);
    }, [authMode, loginMethod, registerMethod, clearError]);

    // Countdown timer for verification code
    useEffect(() => {
        if (countDown > 0) {
            const timer = setTimeout(() => setCountDown(countDown - 1), 1000);
            return () => clearTimeout(timer);
        }
    }, [countDown]);

    // Update form field
    const updateField = useCallback((field: keyof FormData, value: string) => {
        setFormData(prev => ({ ...prev, [field]: value }));
        // Clear field error when user types
        if (errors[field]) {
            setErrors(prev => ({ ...prev, [field]: undefined }));
        }
    }, [errors]);

    // Validate form
    const validateForm = useCallback((): boolean => {
        const newErrors: FormErrors = {};

        if (authMode === 'login' && loginMethod === 'password') {
            if (!formData.identifier.trim()) {
                newErrors.identifier = t.required;
            }
            if (!formData.password) {
                newErrors.password = t.required;
            } else if (formData.password.length < 6) {
                newErrors.password = t.passwordTooShort;
            }
        }

        if (authMode === 'register') {
            if (registerMethod === 'email') {
                if (!formData.email.trim()) {
                    newErrors.email = t.required;
                } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
                    newErrors.email = t.invalidEmail;
                }
                if (!formData.username.trim()) {
                    newErrors.username = t.required;
                } else if (formData.username.length < 3) {
                    newErrors.username = t.usernameTooShort;
                }
            }
            if (!formData.password) {
                newErrors.password = t.required;
            } else if (formData.password.length < 8) {
                newErrors.password = t.passwordTooShort;
            } else if (getPasswordStrength(formData.password) === 'weak') {
                newErrors.password = t.passwordRequirements;
            }
            if (formData.password !== formData.confirmPassword) {
                newErrors.confirmPassword = t.passwordMismatch;
            }
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    }, [authMode, loginMethod, registerMethod, formData, t]);

    // Handle form submission
    const handleSubmit = useCallback(async () => {
        if (!validateForm()) return;

        try {
            if (authMode === 'login' && loginMethod === 'password') {
                await login(formData.identifier, formData.password);
                onLogin();
            } else if (authMode === 'register' && registerMethod === 'email') {
                await register(
                    formData.username,
                    formData.email,
                    formData.password,
                    formData.username // Use username as displayName
                );
                onLogin();
            }
        } catch (err) {
            // Error is handled by AuthStore, but we can add additional handling here
            console.error('Auth error:', err);
        }
    }, [authMode, loginMethod, registerMethod, formData, validateForm, login, register, onLogin]);

    const handleSendCode = () => {
        setCountDown(60);
        // TODO: Implement actual send code logic
    };

    // Handle Enter key press
    const handleKeyPress = useCallback((e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !isLoading) {
            handleSubmit();
        }
    }, [handleSubmit, isLoading]);

    return (
        <div className="min-h-screen w-full bg-black flex overflow-hidden font-sans">
            {/* Left Side - Immersive Image */}
            <div className="hidden lg:block lg:w-1/2 relative overflow-hidden bg-zinc-950">
                {/* Spotlight Effect */}
                <div
                    className="absolute bottom-0 left-0 w-full h-full z-0"
                    style={{
                        background: 'radial-gradient(circle at 10% 90%, rgba(124, 58, 237, 0.25) 0%, transparent 50%)'
                    }}
                />

                {/* Background Image */}
                <img
                    src="/assets/InkFlow_back.png"
                    alt="InkFlow Atmosphere"
                    className="absolute inset-0 object-cover w-full h-full scale-110 animate-slow-pan transition-opacity duration-700 ease-in-out"
                    style={{
                        opacity: 0.5,
                        mixBlendMode: 'screen',
                        maskImage: 'linear-gradient(to right, black 40%, transparent 100%)',
                        WebkitMaskImage: 'linear-gradient(to right, black 40%, transparent 100%)'
                    }}
                />

                {/* Quote Overlay */}
                <div className="absolute bottom-20 left-12 z-20 max-w-lg select-none">
                    <div className="text-violet-500/50 text-6xl font-serif leading-none -mb-4 opacity-80">"</div>
                    <h2 className="text-3xl md:text-4xl font-serif font-medium text-zinc-100 mb-6 leading-relaxed tracking-wide drop-shadow-lg">
                        {t.quote}
                    </h2>
                    <div className="flex items-center gap-3">
                        <div className="h-[1px] w-8 bg-violet-500/50"></div>
                        <p className="text-zinc-400 text-sm md:text-base font-light tracking-widest uppercase">
                            {t.author}
                        </p>
                    </div>
                </div>
            </div>

            {/* Right Side - Form Container */}
            <div className="w-full lg:w-1/2 flex items-center justify-center p-8 bg-[#09090b] relative">
                {/* Ambient Glow */}
                <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-violet-900/20 rounded-full blur-[120px] pointer-events-none" />

                {/* Language Toggle */}
                <div className="absolute top-8 right-8 z-20">
                    <button
                        onClick={() => setLang(lang === 'zh' ? 'en' : 'zh')}
                        className="flex items-center gap-2 text-zinc-500 hover:text-white transition-colors border border-zinc-800 hover:border-zinc-600 rounded-full px-4 py-2 text-xs uppercase tracking-widest font-medium group"
                    >
                        <Globe className="h-3 w-3 group-hover:rotate-180 transition-transform duration-500" />
                        <span>{lang === 'zh' ? 'EN' : '中'}</span>
                    </button>
                </div>

                <div className="w-full max-w-[420px] relative z-10" onKeyPress={handleKeyPress}>
                    {/* Header / Logo */}
                    <motion.div
                        layout
                        className="flex flex-col items-center mb-10"
                    >
                        <div className="w-14 h-14 rounded-2xl bg-gradient-to-tr from-violet-600 to-indigo-600 flex items-center justify-center shadow-[0_0_30px_rgba(124,58,237,0.3)] mb-5">
                            <PenTool className="h-7 w-7 text-white" />
                        </div>
                        <h1 className="text-3xl font-serif font-medium text-white mb-2 tracking-tight antialiased">
                            {authMode === 'login' ? t.welcomeBack : authMode === 'register' ? t.joinUs : t.resetPass}
                        </h1>
                        <p className="text-zinc-500 text-sm font-light italic tracking-wider">{t.slogan}</p>
                    </motion.div>

                    {/* Error Alert */}
                    {authError && (
                        <motion.div
                            initial={{ opacity: 0, y: -10 }}
                            animate={{ opacity: 1, y: 0 }}
                            className="mb-6 p-4 bg-red-500/10 border border-red-500/20 rounded-xl flex items-start gap-3"
                        >
                            <AlertCircle className="h-5 w-5 text-red-400 shrink-0 mt-0.5" />
                            <p className="text-red-400 text-sm">{authError}</p>
                        </motion.div>
                    )}

                    {/* Auth Method Tabs (Only for Login/Register) */}
                    {authMode !== 'forgot-password' && (
                        <div className="relative mb-8">
                            {/* QR Code Icon - Top Right Corner (Login only) */}
                            {authMode === 'login' && loginMethod !== 'wechat' && (
                                <button
                                    onClick={() => setLoginMethod('wechat')}
                                    className="absolute -top-16 right-0 p-2.5 rounded-xl border border-zinc-800 hover:border-violet-500/50 hover:bg-violet-500/10 text-zinc-500 hover:text-violet-400 transition-all group"
                                    title={t.methodWechat}
                                >
                                    <QrCode className="h-5 w-5 group-hover:scale-110 transition-transform" />
                                </button>
                            )}
                            {/* Back from QR Code */}
                            {authMode === 'login' && loginMethod === 'wechat' && (
                                <button
                                    onClick={() => setLoginMethod('password')}
                                    className="absolute -top-16 right-0 p-2.5 rounded-xl border border-zinc-800 hover:border-violet-500/50 hover:bg-violet-500/10 text-zinc-500 hover:text-violet-400 transition-all group"
                                    title={t.methodPassword}
                                >
                                    <ArrowLeft className="h-5 w-5 group-hover:scale-110 transition-transform" />
                                </button>
                            )}
                            
                            {/* Simplified Tab Design - Underline Style */}
                            <div className="flex justify-center gap-8">
                                {authMode === 'login' && loginMethod !== 'wechat' ? (
                                    <>
                                        <UnderlineTab 
                                            active={loginMethod === 'password'} 
                                            onClick={() => setLoginMethod('password')} 
                                            label={t.methodPassword} 
                                        />
                                        <UnderlineTab 
                                            active={loginMethod === 'mobile'} 
                                            onClick={() => setLoginMethod('mobile')} 
                                            label={t.methodMobile} 
                                        />
                                    </>
                                ) : authMode === 'register' ? (
                                    <>
                                        <UnderlineTab 
                                            active={registerMethod === 'mobile'} 
                                            onClick={() => setRegisterMethod('mobile')} 
                                            label={t.methodMobileReg} 
                                        />
                                        <UnderlineTab 
                                            active={registerMethod === 'email'} 
                                            onClick={() => setRegisterMethod('email')} 
                                            label={t.methodEmailReg} 
                                        />
                                    </>
                                ) : null}
                            </div>
                        </div>
                    )}

                    <AnimatePresence mode="wait">
                        <motion.div
                            key={`${authMode}-${loginMethod}-${registerMethod}`}
                            initial={{ opacity: 0, x: 20 }}
                            animate={{ opacity: 1, x: 0 }}
                            exit={{ opacity: 0, x: -20 }}
                            transition={{ duration: 0.3 }}
                            className="space-y-6"
                        >
                            {/* --- LOGIN FORMS --- */}
                            {authMode === 'login' && loginMethod === 'password' && (
                                <>
                                    <InputGroup 
                                        label={t.usernameLabel} 
                                        icon={User} 
                                        type="text" 
                                        placeholder={t.usernamePlaceholder}
                                        value={formData.identifier}
                                        onChange={(v) => updateField('identifier', v)}
                                        error={errors.identifier}
                                    />
                                    <InputGroup 
                                        label={t.passwordLabel} 
                                        icon={Lock} 
                                        type="password" 
                                        placeholder={t.passwordPlaceholder}
                                        value={formData.password}
                                        onChange={(v) => updateField('password', v)}
                                        error={errors.password}
                                    />
                                </>
                            )}

                            {authMode === 'login' && loginMethod === 'mobile' && (
                                <>
                                    <InputGroup 
                                        label={t.mobileLabel} 
                                        icon={Smartphone} 
                                        type="tel" 
                                        placeholder={t.mobilePlaceholder}
                                        value={formData.mobile}
                                        onChange={(v) => updateField('mobile', v)}
                                        error={errors.mobile}
                                    />
                                    <VerificationInput 
                                        label={t.codeLabel} 
                                        placeholder={t.codePlaceholder} 
                                        onSend={handleSendCode} 
                                        countdown={countDown} 
                                        sentText={t.sent} 
                                        sendText={t.sendCode}
                                        value={formData.code}
                                        onChange={(v) => updateField('code', v)}
                                        error={errors.code}
                                    />
                                </>
                            )}

                            {authMode === 'login' && loginMethod === 'wechat' && (
                                <div className="flex flex-col items-center justify-center p-8 bg-white/5 rounded-2xl border border-white/10">
                                    <div className="w-48 h-48 bg-white rounded-xl flex items-center justify-center mb-4">
                                        <QrCode className="h-32 w-32 text-zinc-900" />
                                    </div>
                                    <p className="text-zinc-400 text-sm text-center">{t.wechatScan}</p>
                                </div>
                            )}

                            {/* --- REGISTER FORMS --- */}
                            {authMode === 'register' && (
                                <>
                                    {registerMethod === 'mobile' ? (
                                        <>
                                            <InputGroup 
                                                label={t.mobileLabel} 
                                                icon={Smartphone} 
                                                type="tel" 
                                                placeholder={t.mobilePlaceholder}
                                                value={formData.mobile}
                                                onChange={(v) => updateField('mobile', v)}
                                                error={errors.mobile}
                                            />
                                            <VerificationInput 
                                                label={t.codeLabel} 
                                                placeholder={t.codePlaceholder} 
                                                onSend={handleSendCode} 
                                                countdown={countDown} 
                                                sentText={t.sent} 
                                                sendText={t.sendCode}
                                                value={formData.code}
                                                onChange={(v) => updateField('code', v)}
                                                error={errors.code}
                                            />
                                        </>
                                    ) : (
                                        <>
                                            <InputGroup 
                                                label={t.usernameLabel.split('/')[0].trim()} 
                                                icon={User} 
                                                type="text" 
                                                placeholder={t.usernamePlaceholder}
                                                value={formData.username}
                                                onChange={(v) => updateField('username', v)}
                                                error={errors.username}
                                            />
                                            <InputGroup 
                                                label={t.emailLabel} 
                                                icon={Mail} 
                                                type="email" 
                                                placeholder={t.emailPlaceholder}
                                                value={formData.email}
                                                onChange={(v) => updateField('email', v)}
                                                error={errors.email}
                                            />
                                        </>
                                    )}
                                    <div className="space-y-2">
                                        <InputGroup 
                                            label={t.passwordLabel} 
                                            icon={Lock} 
                                            type="password" 
                                            placeholder={t.passwordPlaceholder}
                                            value={formData.password}
                                            onChange={(v) => updateField('password', v)}
                                            error={errors.password}
                                        />
                                        {formData.password && (
                                            <PasswordStrengthIndicator 
                                                strength={getPasswordStrength(formData.password)} 
                                                translations={t}
                                            />
                                        )}
                                    </div>
                                    <InputGroup 
                                        label={t.confirmPasswordLabel} 
                                        icon={Lock} 
                                        type="password" 
                                        placeholder={t.confirmPasswordPlaceholder}
                                        value={formData.confirmPassword}
                                        onChange={(v) => updateField('confirmPassword', v)}
                                        error={errors.confirmPassword}
                                    />
                                </>
                            )}

                            {/* --- FORGOT PASSWORD FORM --- */}
                            {authMode === 'forgot-password' && (
                                <>
                                    <InputGroup 
                                        label={t.emailLabel} 
                                        icon={Mail} 
                                        type="email" 
                                        placeholder={t.emailPlaceholder}
                                        value={formData.email}
                                        onChange={(v) => updateField('email', v)}
                                        error={errors.email}
                                    />
                                    <VerificationInput 
                                        label={t.codeLabel} 
                                        placeholder={t.codePlaceholder} 
                                        onSend={handleSendCode} 
                                        countdown={countDown} 
                                        sentText={t.sent} 
                                        sendText={t.sendCode}
                                        value={formData.code}
                                        onChange={(v) => updateField('code', v)}
                                        error={errors.code}
                                    />
                                    <InputGroup 
                                        label={t.passwordLabel} 
                                        icon={Lock} 
                                        type="password" 
                                        placeholder={t.passwordPlaceholder}
                                        value={formData.password}
                                        onChange={(v) => updateField('password', v)}
                                        error={errors.password}
                                    />
                                </>
                            )}

                            {/* Action Button */}
                            {loginMethod !== 'wechat' && (
                                <div className="pt-2">
                                    <Button
                                        className="w-full h-14 rounded-full bg-white text-zinc-950 hover:bg-zinc-100 text-lg font-medium shadow-[0_0_20px_rgba(255,255,255,0.35)] hover:shadow-[0_0_25px_rgba(255,255,255,0.5)] transition-all active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed"
                                        onClick={handleSubmit}
                                        disabled={isLoading}
                                    >
                                        {isLoading ? (
                                            <>
                                                <Loader2 className="mr-2 h-5 w-5 animate-spin" />
                                                {lang === 'zh' ? '处理中...' : 'Processing...'}
                                            </>
                                        ) : (
                                            <>
                                                {authMode === 'login' ? t.loginBtn : authMode === 'register' ? t.createBtn : t.resetBtn}
                                                <ArrowRight className="ml-2 h-5 w-5" />
                                            </>
                                        )}
                                    </Button>
                                </div>
                            )}

                            {/* Mode Toggles */}
                            <div className="flex flex-col gap-3 items-center text-sm pt-2">
                                {authMode === 'login' && (
                                    <>
                                        <button onClick={() => setAuthMode('forgot-password')} className="text-zinc-500 hover:text-zinc-300 transition-colors">
                                            {t.forgotPass}
                                        </button>
                                        <button onClick={() => setAuthMode('register')} className="text-zinc-400 hover:text-white transition-colors">
                                            {t.noAccount}
                                        </button>
                                    </>
                                )}

                                {authMode === 'register' && (
                                    <button onClick={() => setAuthMode('login')} className="text-zinc-400 hover:text-white transition-colors">
                                        {t.hasAccount}
                                    </button>
                                )}

                                {authMode === 'forgot-password' && (
                                    <button onClick={() => setAuthMode('login')} className="flex items-center gap-2 text-zinc-400 hover:text-white transition-colors">
                                        <ArrowLeft className="h-4 w-4" />
                                        {t.backToLogin}
                                    </button>
                                )}
                            </div>
                        </motion.div>
                    </AnimatePresence>
                </div>

                {/* Footer info */}
                <div className="absolute bottom-8 text-zinc-700 text-xs tracking-widest">
                    {t.footer}
                </div>
            </div>
        </div>
    );
}

// Sub-components for cleaner code

// New underline-style tab for cleaner look
function UnderlineTab({ active, onClick, label }: { active: boolean; onClick: () => void; label: string }) {
    return (
        <button
            onClick={onClick}
            className={`relative pb-2 text-sm font-medium transition-all ${
                active ? 'text-white' : 'text-zinc-500 hover:text-zinc-300'
            }`}
        >
            <span>{label}</span>
            {/* Animated underline */}
            <motion.div
                className="absolute bottom-0 left-0 right-0 h-0.5 bg-violet-500 rounded-full"
                initial={false}
                animate={{ 
                    scaleX: active ? 1 : 0,
                    opacity: active ? 1 : 0
                }}
                transition={{ duration: 0.2 }}
            />
        </button>
    );
}



interface InputGroupProps {
    label: string;
    icon: React.ComponentType<{ className?: string }>;
    type: string;
    placeholder: string;
    value: string;
    onChange: (value: string) => void;
    error?: string;
}

function InputGroup({ label, icon: Icon, type, placeholder, value, onChange, error }: InputGroupProps) {
    return (
        <div className="space-y-2 group">
            <label className={`text-xs uppercase tracking-widest font-semibold transition-colors ${error ? 'text-red-400' : 'text-zinc-400 group-focus-within:text-violet-500'}`}>
                {label}
            </label>
            <div className="relative">
                <input
                    type={type}
                    value={value}
                    onChange={(e) => onChange(e.target.value)}
                    className={`w-full bg-transparent border-b py-3 pl-0 pr-8 outline-none transition-all font-light placeholder:text-zinc-600 focus:placeholder:text-zinc-500 ${
                        error 
                            ? 'border-red-500/50 text-red-200 focus:border-red-500' 
                            : 'border-white/10 text-zinc-200 focus:border-violet-500'
                    }`}
                    placeholder={placeholder}
                />
                <Icon className={`absolute right-0 top-1/2 -translate-y-1/2 h-5 w-5 transition-colors ${error ? 'text-red-400' : 'text-zinc-600 group-focus-within:text-violet-500'}`} />
            </div>
            {error && (
                <p className="text-red-400 text-xs mt-1">{error}</p>
            )}
        </div>
    );
}

interface VerificationInputProps {
    label: string;
    placeholder: string;
    onSend: () => void;
    countdown: number;
    sentText: string;
    sendText: string;
    value: string;
    onChange: (value: string) => void;
    error?: string;
}

function VerificationInput({ label, placeholder, onSend, countdown, sentText, sendText, value, onChange, error }: VerificationInputProps) {
    return (
        <div className="space-y-2 group">
            <label className={`text-xs uppercase tracking-widest font-semibold transition-colors ${error ? 'text-red-400' : 'text-zinc-400 group-focus-within:text-violet-500'}`}>
                {label}
            </label>
            <div className="relative flex gap-4">
                <div className="relative flex-1">
                    <input
                        type="text"
                        value={value}
                        onChange={(e) => onChange(e.target.value)}
                        className={`w-full bg-transparent border-b py-3 pl-0 pr-8 outline-none transition-all font-light placeholder:text-zinc-600 focus:placeholder:text-zinc-500 ${
                            error 
                                ? 'border-red-500/50 text-red-200 focus:border-red-500' 
                                : 'border-white/10 text-zinc-200 focus:border-violet-500'
                        }`}
                        placeholder={placeholder}
                    />
                    <Hash className={`absolute right-0 top-1/2 -translate-y-1/2 h-5 w-5 transition-colors ${error ? 'text-red-400' : 'text-zinc-600 group-focus-within:text-violet-500'}`} />
                </div>
                <button
                    onClick={onSend}
                    disabled={countdown > 0}
                    className="shrink-0 w-28 border-b border-white/10 text-zinc-400 hover:text-white hover:border-white/30 text-xs font-medium transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                >
                    {countdown > 0 ? `${countdown}s ${sentText}` : sendText}
                </button>
            </div>
            {error && (
                <p className="text-red-400 text-xs mt-1">{error}</p>
            )}
        </div>
    );
}


interface PasswordStrengthIndicatorProps {
    strength: PasswordStrength;
    translations: typeof translations.zh;
}

function PasswordStrengthIndicator({ strength, translations: t }: PasswordStrengthIndicatorProps) {
    const strengthConfig = {
        weak: { 
            color: 'bg-red-500', 
            width: 'w-1/3', 
            text: t.passwordWeak,
            textColor: 'text-red-400'
        },
        medium: { 
            color: 'bg-yellow-500', 
            width: 'w-2/3', 
            text: t.passwordMedium,
            textColor: 'text-yellow-400'
        },
        strong: { 
            color: 'bg-green-500', 
            width: 'w-full', 
            text: t.passwordStrong,
            textColor: 'text-green-400'
        },
    };

    const config = strengthConfig[strength];

    return (
        <div className="space-y-1">
            <div className="h-1 bg-zinc-800 rounded-full overflow-hidden">
                <motion.div
                    initial={{ width: 0 }}
                    animate={{ width: strength === 'weak' ? '33%' : strength === 'medium' ? '66%' : '100%' }}
                    className={`h-full ${config.color} rounded-full`}
                    transition={{ duration: 0.3 }}
                />
            </div>
            <p className={`text-xs ${config.textColor}`}>{config.text}</p>
        </div>
    );
}
