
import { QRCodeSVG } from 'qrcode.react';

export default function TicketCard({ ticket, label }) {
  return (
    <div className={`ticket-card${ticket.checkedIn ? ' ticket-used' : ''}`}>
      <div className="ticket-qr">
        <QRCodeSVG
          value={ticket.code}
          size={220}
          level="H"
          marginSize={4}
          bgColor="#ffffff"
          fgColor="#000000"
          title={`Ticket ${ticket.code}`}
        />
      </div>

      <div className="ticket-info">
        <strong>{label}</strong>
        <span className="ticket-code">{ticket.code}</span>

        {ticket.checkedIn ? (
          <span className="status status-completed">Used</span>
        ) : (
          <span className="status status-published">Valid</span>
        )}
      </div>
    </div>
  );
}











// import { QRCodeSVG } from 'qrcode.react';

// export default function TicketCard({ ticket, label }) {
//   return (
//     <div className={`ticket-card${ticket.checkedIn ? ' ticket-used' : ''}`}>
//       <div className="ticket-qr">
//         <QRCodeSVG value={ticket.code} size={132} level="M" />
//       </div>
//       <div className="ticket-info">
//         <strong>{label}</strong>
//         <span className="ticket-code">{ticket.code}</span>
//         {ticket.checkedIn ? (
//           <span className="status status-completed">Used</span>
//         ) : (
//           <span className="status status-published">Valid</span>
//         )}
//       </div>
//     </div>
//   );
// }