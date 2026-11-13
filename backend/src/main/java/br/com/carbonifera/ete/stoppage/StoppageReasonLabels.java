package br.com.carbonifera.ete.stoppage;

public final class StoppageReasonLabels {

    private StoppageReasonLabels() {
    }

    public static String of(StoppageReason reason) {
        return switch (reason) {
            case FALTA_DE_AGUA -> "Falta de água";
            case FALTA_DE_OPERADOR -> "Falta de operador";
            case FALTA_DE_CAL -> "Falta de cal";
            case DIRECIONAMENTO_DE_AGUA_PARA_LAVADOR -> "Direcionamento de água para lavador";
            case ENTUPIMENTO_DE_BOMBAS -> "Entupimento de bombas";
            case OUTROS -> "Outros";
        };
    }

    public static String describe(StoppageReason reason, String reasonDetail) {
        String label = of(reason);
        return reasonDetail == null || reasonDetail.isBlank() ? label : label + " - " + reasonDetail;
    }
}
