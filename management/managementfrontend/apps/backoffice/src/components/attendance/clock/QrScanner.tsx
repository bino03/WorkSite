import { useEffect, useRef, useState } from "react";
import { Alert, Button } from "antd";
import jsQR from "jsqr";

interface Props {
  /** O texto lido do QR. Chamado uma vez; o leitor para a seguir. */
  onDetected: (text: string) => void;
  onCancel: () => void;
}

/**
 * Lê um QR pela câmara traseira do telemóvel, dentro da app — para o funcionário picar
 * a partir do ecrã inicial sem ter de sair para a app da câmara. `jsqr` e não o
 * `BarcodeDetector` do browser: o Safari do iPhone não o tem.
 *
 * A câmara só abre em HTTPS (ou localhost) — é uma regra do browser, não nossa.
 */
export function QrScanner({ onDetected, onCancel }: Props) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [problem, setProblem] = useState<string | null>(null);

  useEffect(() => {
    let stream: MediaStream | null = null;
    let frame = 0;
    let stopped = false;

    const stop = () => {
      stopped = true;
      cancelAnimationFrame(frame);
      stream?.getTracks().forEach((track) => track.stop());
    };

    const scan = () => {
      if (stopped) return;
      const video = videoRef.current;
      const canvas = canvasRef.current;
      if (video && canvas && video.readyState === video.HAVE_ENOUGH_DATA) {
        // Reduzido: um QR colado na parede lê-se bem a 640 px, e poupa a bateria.
        const scale = Math.min(1, 640 / video.videoWidth);
        canvas.width = Math.round(video.videoWidth * scale);
        canvas.height = Math.round(video.videoHeight * scale);
        const context = canvas.getContext("2d", { willReadFrequently: true });
        if (context) {
          context.drawImage(video, 0, 0, canvas.width, canvas.height);
          const image = context.getImageData(0, 0, canvas.width, canvas.height);
          const result = jsQR(image.data, image.width, image.height, { inversionAttempts: "dontInvert" });
          if (result?.data) {
            stop();
            onDetected(result.data);
            return;
          }
        }
      }
      frame = requestAnimationFrame(scan);
    };

    (async () => {
      if (!navigator.mediaDevices?.getUserMedia) {
        setProblem("Este browser não dá acesso à câmara. Leia o QR com a app da câmara do telemóvel.");
        return;
      }
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          video: { facingMode: { ideal: "environment" } },
          audio: false,
        });
        if (stopped) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }
        const video = videoRef.current;
        if (!video) return;
        video.srcObject = stream;
        await video.play();
        frame = requestAnimationFrame(scan);
      } catch {
        setProblem(
          "Não foi possível abrir a câmara. Autorize o acesso à câmara nas definições do browser, ou leia o QR com a app da câmara."
        );
      }
    })();

    return stop;
  }, [onDetected]);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--ind-space-4)" }}>
      {problem ? (
        <Alert type="warning" showIcon message={problem} />
      ) : (
        <div
          style={{
            position: "relative",
            width: "100%",
            aspectRatio: "1 / 1",
            overflow: "hidden",
            borderRadius: 12,
            background: "var(--ind-neutral-900)",
          }}
        >
          <video
            ref={videoRef}
            playsInline
            muted
            style={{ width: "100%", height: "100%", objectFit: "cover" }}
          />
          {/* Moldura: diz onde pôr o QR. */}
          <div
            style={{
              position: "absolute",
              inset: "15%",
              border: "3px solid var(--ind-neutral-100)",
              borderRadius: 12,
              pointerEvents: "none",
            }}
          />
        </div>
      )}
      <canvas ref={canvasRef} style={{ display: "none" }} />
      <p className="ind-card-meta" style={{ margin: 0, textAlign: "center" }}>
        Aponte a câmara para o QR afixado na obra.
      </p>
      <Button block size="large" onClick={onCancel}>
        Cancelar
      </Button>
    </div>
  );
}
