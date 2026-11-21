using System.Runtime.InteropServices;
using NAudio.CoreAudioApi;
using Windows.Devices.Bluetooth;
using Windows.Devices.Enumeration;

namespace BudsControlPC.Buds;

/// <summary>
/// Liga/desliga o áudio de um fone Bluetooth já pareado, sem abrir as configurações do Windows.
/// Usa a propriedade KSPROPSETID_BtAudio do driver de áudio Bluetooth (ONESHOT_RECONNECT / ONESHOT_DISCONNECT),
/// o mesmo mecanismo do botão "Conectar" do painel de Bluetooth.
/// </summary>
public static class BtAudio
{
    private static readonly Guid KsPropSetIdBtAudio = new("7FA06C40-B8F6-4C7E-8556-E8C33A12E54D");
    private const uint KsPropertyOneshotReconnect = 0;
    private const uint KsPropertyOneshotDisconnect = 1;
    private const uint KsPropertyTypeGet = 1;
    private const int ClsctxAll = 0x17;

    [StructLayout(LayoutKind.Sequential)]
    private struct KsProperty
    {
        public Guid Set;
        public uint Id;
        public uint Flags;
    }

    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    private class MMDeviceEnumeratorCom { }

    [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDeviceEnumerator
    {
        [PreserveSig] int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
        [PreserveSig] int GetDefaultAudioEndpoint(int dataFlow, int role, out IntPtr endpoint);
        [PreserveSig] int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string id, out IMMDevice device);
    }

    [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDevice
    {
        [PreserveSig] int Activate(ref Guid iid, int clsCtx, IntPtr activationParams, [MarshalAs(UnmanagedType.IUnknown)] out object iface);
    }

    [ComImport, Guid("2A07407E-6497-4A18-9787-32F79BD0D98F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IDeviceTopology
    {
        [PreserveSig] int GetConnectorCount(out uint count);
        [PreserveSig] int GetConnector(uint index, out IConnector connector);
    }

    [ComImport, Guid("9c2c4058-23f5-41de-877a-df3af236a09e"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IConnector
    {
        [PreserveSig] int GetConnectorType(out int type);
        [PreserveSig] int GetDataFlow(out int flow);
        [PreserveSig] int ConnectTo(IConnector other);
        [PreserveSig] int Disconnect();
        [PreserveSig] int IsConnected([MarshalAs(UnmanagedType.Bool)] out bool connected);
        [PreserveSig] int GetConnectedTo(out IConnector other);
        [PreserveSig] int GetConnectorIdConnectedTo([MarshalAs(UnmanagedType.LPWStr)] out string id);
        [PreserveSig] int GetDeviceIdConnectedTo([MarshalAs(UnmanagedType.LPWStr)] out string id);
    }

    [ComImport, Guid("AE2DE0E4-5BCA-4F2D-AA46-5D13F8FDB3A9"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IPart
    {
        [PreserveSig] int GetName(out IntPtr name);
        [PreserveSig] int GetLocalId(out uint id);
        [PreserveSig] int GetGlobalId(out IntPtr id);
        [PreserveSig] int GetPartType(out int type);
        [PreserveSig] int GetSubType(out Guid subType);
        [PreserveSig] int GetControlInterfaceCount(out uint count);
        [PreserveSig] int GetControlInterface(uint index, out IntPtr iface);
        [PreserveSig] int EnumPartsIncoming(out IntPtr parts);
        [PreserveSig] int EnumPartsOutgoing(out IntPtr parts);
        [PreserveSig] int GetTopologyObject(out IntPtr topology);
        [PreserveSig] int Activate(int clsContext, ref Guid iid, [MarshalAs(UnmanagedType.IUnknown)] out object iface);
    }

    [ComImport, Guid("28F54685-06FD-11D2-B27A-00A0C9223196"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IKsControl
    {
        [PreserveSig] int KsProperty(ref KsProperty property, uint propertyLength, IntPtr data, uint dataLength, out uint bytesReturned);
    }

    /// <summary>Endereço MAC em hexadecimal (12 dígitos), usado para achar os endpoints de áudio do fone.</summary>
    public static async Task<string?> MacOf(string deviceId)
    {
        try
        {
            var dev = await BluetoothDevice.FromIdAsync(deviceId);
            return dev?.BluetoothAddress.ToString("X12");
        }
        catch { return null; }
    }

    /// <summary>Envia reconectar (true) ou desconectar (false) a todos os endpoints de áudio do fone. Retorna quantos responderam.</summary>
    public static int Send(string mac, string? name, bool connect, Action<string>? log = null)
    {
        int ok = 0;
        var en = (IMMDeviceEnumerator)new MMDeviceEnumeratorCom();
        using var naudio = new MMDeviceEnumerator();
        foreach (var ep in naudio.EnumerateAudioEndPoints(DataFlow.All, DeviceState.All))
        {
            string epId;
            try { epId = ep.ID; } catch { continue; }
            try
            {
                if (en.GetDevice(epId, out var dev) != 0 || dev == null) continue;
                var iidTopo = typeof(IDeviceTopology).GUID;
                if (dev.Activate(ref iidTopo, ClsctxAll, IntPtr.Zero, out var topoObj) != 0) continue;
                var topo = (IDeviceTopology)topoObj;
                if (topo.GetConnector(0, out var conn) != 0) continue;
                if (conn.GetDeviceIdConnectedTo(out var connectedId) != 0 || connectedId == null) continue;

                // só endpoints Bluetooth, e só os deste fone
                if (!connectedId.Contains("BTHENUM", StringComparison.OrdinalIgnoreCase) &&
                    !connectedId.Contains("BTHHFENUM", StringComparison.OrdinalIgnoreCase)) continue;
                bool mine = connectedId.Contains(mac, StringComparison.OrdinalIgnoreCase);
                if (!mine && name != null)
                {
                    try { mine = ep.FriendlyName.Contains(name, StringComparison.OrdinalIgnoreCase); } catch { }
                }
                if (!mine) continue;

                if (conn.GetConnectedTo(out var other) != 0 || other is not IPart part) continue;
                var iidKs = typeof(IKsControl).GUID;
                if (part.Activate(ClsctxAll, ref iidKs, out var ksObj) != 0) continue;
                var ks = (IKsControl)ksObj;
                var prop = new KsProperty
                {
                    Set = KsPropSetIdBtAudio,
                    Id = connect ? KsPropertyOneshotReconnect : KsPropertyOneshotDisconnect,
                    Flags = KsPropertyTypeGet,
                };
                int hr = ks.KsProperty(ref prop, (uint)Marshal.SizeOf<KsProperty>(), IntPtr.Zero, 0, out _);
                log?.Invoke($"{(connect ? "Conectar" : "Desconectar")} áudio em \"{SafeName(ep)}\": {(hr == 0 ? "ok" : $"0x{hr:X8}")}");
                if (hr == 0) ok++;
            }
            catch (Exception e)
            {
                log?.Invoke($"Endpoint ignorado: {e.Message}");
            }
        }
        return ok;
    }

    private static string SafeName(MMDevice d)
    {
        try { return d.FriendlyName; } catch { return d.ID; }
    }

    /// <summary>Se o Windows considera o fone conectado agora.</summary>
    public static async Task<bool> IsConnectedAsync(string deviceId)
    {
        try
        {
            var dev = await BluetoothDevice.FromIdAsync(deviceId);
            return dev?.ConnectionStatus == BluetoothConnectionStatus.Connected;
        }
        catch { return false; }
    }

    // ------------------------------------------------------------ busca e pareamento

    public sealed record FoundDevice(string Id, string Name)
    {
        public override string ToString() => Name;
    }

    /// <summary>Procura aparelhos Bluetooth próximos ainda não pareados (coloque os fones em modo de pareamento).</summary>
    public static async Task<List<FoundDevice>> ScanAsync(TimeSpan duration, Action<FoundDevice>? onFound = null)
    {
        var found = new Dictionary<string, FoundDevice>();
        // Bluetooth clássico (fones), ainda não pareados
        const string aqs = "System.Devices.Aep.ProtocolId:=\"{e0cbf06c-cd8b-4647-bb8a-263b43f0f974}\" AND System.Devices.Aep.IsPaired:=System.StructuredQueryType.Boolean#False";
        var watcher = DeviceInformation.CreateWatcher(
            aqs,
            new[] { "System.Devices.Aep.IsPresent" },
            DeviceInformationKind.AssociationEndpoint);
        watcher.Added += (_, info) =>
        {
            if (string.IsNullOrWhiteSpace(info.Name)) return;
            var f = new FoundDevice(info.Id, info.Name);
            lock (found)
            {
                if (found.ContainsKey(info.Id)) return;
                found[info.Id] = f;
            }
            onFound?.Invoke(f);
        };
        watcher.Start();
        await Task.Delay(duration);
        try { watcher.Stop(); } catch { }
        lock (found) return found.Values.ToList();
    }

    /// <summary>Pareia aceitando automaticamente a confirmação (fones não têm teclado).</summary>
    public static async Task<(bool Ok, string Message)> PairAsync(string id)
    {
        var info = await DeviceInformation.CreateFromIdAsync(id);
        if (info.Pairing.IsPaired) return (true, "Já estava pareado.");
        var custom = info.Pairing.Custom;
        void Handler(DeviceInformationCustomPairing s, DevicePairingRequestedEventArgs a)
        {
            if (a.PairingKind == DevicePairingKinds.ProvidePin) a.Accept("0000");
            else a.Accept();
        }
        custom.PairingRequested += Handler;
        try
        {
            var r = await custom.PairAsync(
                DevicePairingKinds.ConfirmOnly | DevicePairingKinds.ConfirmPinMatch |
                DevicePairingKinds.DisplayPin | DevicePairingKinds.ProvidePin);
            return r.Status is DevicePairingResultStatus.Paired or DevicePairingResultStatus.AlreadyPaired
                ? (true, "Pareado com sucesso.")
                : (false, $"Não pareou ({r.Status}). Coloque os fones em modo de pareamento e tente de novo.");
        }
        finally { custom.PairingRequested -= Handler; }
    }
}
