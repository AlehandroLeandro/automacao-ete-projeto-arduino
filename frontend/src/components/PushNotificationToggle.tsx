import { BellOff, BellRing } from "lucide-react";

import { usePushSubscription } from "../hooks/usePushSubscription";

export function PushNotificationToggle(): JSX.Element {
  const push = usePushSubscription();
  const disabled = push.busy || !push.supported || push.permission === "denied";
  const actionLabel = push.subscribed ? "Desativar" : "Ativar";
  const Icon = push.subscribed ? BellRing : BellOff;

  return (
    <section className="surface-panel push-opt-in">
      <div className="push-opt-in__content">
        <span className="push-opt-in__icon">
          <Icon size={20} aria-hidden="true" />
        </span>
        <div>
          <p className="eyebrow">Notificacoes</p>
          <h2>Ativar notificacoes no celular</h2>
          <p>{statusLabel(push.supported, push.permission, push.subscribed)}</p>
        </div>
      </div>

      <div className="push-opt-in__actions">
        <button
          className={push.subscribed ? "button button--secondary" : "button button--primary"}
          disabled={disabled}
          type="button"
          onClick={push.subscribed ? push.unsubscribe : push.subscribe}
        >
          {push.busy ? "Processando..." : actionLabel}
        </button>
        {push.error ? <p className="form-error">{push.error}</p> : null}
        {push.success ? <p className="form-success">{push.success}</p> : null}
      </div>
    </section>
  );
}

function statusLabel(
  supported: boolean,
  permission: NotificationPermission | "unsupported",
  subscribed: boolean,
): string {
  if (!supported) {
    return "Indisponivel neste navegador ou fora de HTTPS/localhost.";
  }
  if (permission === "denied") {
    return "Permissao bloqueada no navegador.";
  }
  if (subscribed) {
    return "Este dispositivo esta inscrito para receber alertas.";
  }
  return "Receba alertas mesmo com o aplicativo fechado.";
}
