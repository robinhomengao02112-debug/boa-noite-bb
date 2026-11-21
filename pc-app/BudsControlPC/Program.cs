namespace BudsControlPC;

internal static class Program
{
    [STAThread]
    private static void Main(string[] args)
    {
        using var mutex = new Mutex(true, "BudsControlPC_SingleInstance", out bool first);
        if (!first)
        {
            MessageBox.Show("O Buds Control PC já está aberto (veja o ícone perto do relógio).", "Buds Control PC");
            return;
        }
        Application.SetUnhandledExceptionMode(UnhandledExceptionMode.CatchException);
        Application.ThreadException += (_, e) =>
            MessageBox.Show($"Erro inesperado:\n{e.Exception.Message}", "Buds Control PC", MessageBoxButtons.OK, MessageBoxIcon.Error);
        ApplicationConfiguration.Initialize();
        Application.Run(new MainForm(args.Contains("--minimized")));
    }
}
